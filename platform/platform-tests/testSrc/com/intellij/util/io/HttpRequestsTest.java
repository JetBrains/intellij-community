/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.ide.IdeBundle;
import com.intellij.util.TimeoutUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpRequestsTest  {
  private static final String LOCALHOST = "127.0.0.1";

  private HttpServer myServer;

  @Before
  public void setUp() throws IOException {
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
  }

  @After
  public void tearDown() {
    myServer.stop(0);
  }

  @Test
  public void testRedirectLimit() {
    try {
      HttpRequests.request("").redirectLimit(0).readString(null);
      fail();
    }
    catch (IOException e) {
      assertEquals(IdeBundle.message("error.connection.failed.redirects"), e.getMessage());
    }
  }

  @Test(timeout = 5000, expected = SocketTimeoutException.class)
  public void testReadTimeout() throws IOException {
    myServer.createContext("/", ex -> {
      TimeoutUtil.sleep(1000);
      ex.sendResponseHeaders(200, 0);
      ex.close();
    });

    String url = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
    HttpRequests.request(url).readTimeout(50).readString(null);
    fail();
  }

  @Test(timeout = 5000)
  public void testDataRead() throws IOException {
    myServer.createContext("/", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/plain");
      ex.sendResponseHeaders(200, 0);
      ex.getResponseBody().write("hello".getBytes("US-ASCII"));
      ex.close();
    });

    String url = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
    String content = HttpRequests.request(url).readString(null);
    assertEquals("hello", content);
  }
}
