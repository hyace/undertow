/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test.session;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * Test that seperate servlet deployments use seperate session managers, even in the presence of forwards,
 * and that sessions created in a forwarded context are accessible to later direct requests
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CrossContextServletSessionTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final ServletContainer container = ServletContainer.Factory.newInstance();
        final CookieHandler cookieHandler = new CookieHandler();
        final PathHandler path = new PathHandler();
        DefaultServer.setRootHandler(cookieHandler);

        createDeployment("1", container, cookieHandler, path);
        createDeployment("2", container, cookieHandler, path);

    }

    private static void createDeployment(final String name, final ServletContainer container, final CookieHandler cookieHandler, final PathHandler path) throws ServletException {
        cookieHandler.setNext(path);
        ServletInfo s = new ServletInfo("servlet", SessionServlet.class)
                .addMapping("/servlet");
        ServletInfo forward = new ServletInfo("forward", ForwardServlet.class)
                .addMapping("/forward");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/" + name)
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName( name + ".war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addServlets(s, forward);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPath(builder.getContextPath(), manager.start());
    }


    @Test
    public void testCrossContextSessionInvocation() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerAddress() + "/1/servlet");
            HttpGet forward1 = new HttpGet(DefaultServer.getDefaultServerAddress() + "/1/forward?context=/2&path=/servlet");
            HttpGet direct2 = new HttpGet(DefaultServer.getDefaultServerAddress() + "/2/servlet");
            HttpGet forward2 = new HttpGet(DefaultServer.getDefaultServerAddress() + "/2/forward?context=/1&path=/servlet");
            HttpResponse result = client.execute(direct1);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(direct1);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(forward2);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(forward2);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);

            result = client.execute(forward1);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(forward1);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(direct2);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(direct2);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    public static class ForwardServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
            req.getServletContext().getContext(req.getParameter("context")).getRequestDispatcher(req.getParameter("path")).forward(req, resp);
        }
    }

}