/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpRequestsTest  {
  private static final String LOCALHOST = "127.0.0.1";

  private HttpServer myServer;
  private String myUrl;

  @Before
  public void setUp() throws IOException {
    myServer = HttpServer.create();
    myServer.bind(new InetSocketAddress(LOCALHOST, 0), 1);
    myServer.start();
    myUrl = "http://" + LOCALHOST + ":" + myServer.getAddress().getPort();
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

    HttpRequests.request(myUrl).readTimeout(50).readString(null);
    fail();
  }

  @Test(timeout = 5000)
  public void testDataRead() throws IOException {
    myServer.createContext("/", ex -> {
      ex.getResponseHeaders().add("Content-Type", "text/plain; charset=koi8-r");
      ex.sendResponseHeaders(200, 0);
      ex.getResponseBody().write("hello кодировочки".getBytes("koi8-r"));
      ex.close();
    });

    assertEquals("hello кодировочки", HttpRequests.request(myUrl).readString(null));
  }

  @Test(timeout = 5000)
  public void testTuning() throws IOException {
    myServer.createContext("/", ex -> {
      ex.sendResponseHeaders("HEAD".equals(ex.getRequestMethod()) ? HTTP_NO_CONTENT : HTTP_NOT_IMPLEMENTED, -1);
      ex.close();
    });

    assertEquals(HTTP_NO_CONTENT, HttpRequests.request(myUrl)
      .tuner((c) -> ((HttpURLConnection)c).setRequestMethod("HEAD"))
      .tryConnect());
  }

  @Test(timeout = 5000)
  public void testNotModified() throws IOException {
    myServer.createContext("/", ex -> {
      ex.sendResponseHeaders(HTTP_NOT_MODIFIED, -1);
      ex.close();
    });

    assertEquals(0, HttpRequests.request(myUrl).readBytes(null).length);
  }
}