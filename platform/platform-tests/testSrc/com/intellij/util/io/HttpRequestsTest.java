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

import static java.net.HttpURLConnection.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpRequestsTest {
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

  @Test
  public void testEncodeURIComponent() throws Exception {
    assertEquals("Test", HttpRequests.encodeURIComponent("Test"));
    String str = "";
    for (int i = 1; i < 256; i++) {
      str += String.valueOf((char)i);
    }
    String expected = "%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F%10%11%12%13%14%15%16%17%18%19%1A%1B%1C%1D%1E%1F%20!%22%23%24%25%26'()*%2B%2C-.%2F0123456789%3A%3B%3C%3D%3E%3F%40ABCDEFGHIJKLMNOPQRSTUVWXYZ%5B%5C%5D%5E_%60abcdefghijklmnopqrstuvwxyz%7B%7C%7D~%7F%C2%80%C2%81%C2%82%C2%83%C2%84%C2%85%C2%86%C2%87%C2%88%C2%89%C2%8A%C2%8B%C2%8C%C2%8D%C2%8E%C2%8F%C2%90%C2%91%C2%92%C2%93%C2%94%C2%95%C2%96%C2%97%C2%98%C2%99%C2%9A%C2%9B%C2%9C%C2%9D%C2%9E%C2%9F%C2%A0%C2%A1%C2%A2%C2%A3%C2%A4%C2%A5%C2%A6%C2%A7%C2%A8%C2%A9%C2%AA%C2%AB%C2%AC%C2%AD%C2%AE%C2%AF%C2%B0%C2%B1%C2%B2%C2%B3%C2%B4%C2%B5%C2%B6%C2%B7%C2%B8%C2%B9%C2%BA%C2%BB%C2%BC%C2%BD%C2%BE%C2%BF%C3%80%C3%81%C3%82%C3%83%C3%84%C3%85%C3%86%C3%87%C3%88%C3%89%C3%8A%C3%8B%C3%8C%C3%8D%C3%8E%C3%8F%C3%90%C3%91%C3%92%C3%93%C3%94%C3%95%C3%96%C3%97%C3%98%C3%99%C3%9A%C3%9B%C3%9C%C3%9D%C3%9E%C3%9F%C3%A0%C3%A1%C3%A2%C3%A3%C3%A4%C3%A5%C3%A6%C3%A7%C3%A8%C3%A9%C3%AA%C3%AB%C3%AC%C3%AD%C3%AE%C3%AF%C3%B0%C3%B1%C3%B2%C3%B3%C3%B4%C3%B5%C3%B6%C3%B7%C3%B8%C3%B9%C3%BA%C3%BB%C3%BC%C3%BD%C3%BE%C3%BF";
    /*
    The expected string is generated in browser using this JavaScript
    var s = '';
    for (var i = 1; i < 256; i++) {
      s += String.fromCodePoint(i);
    }
    console.log(encodeURIComponent(s));
    */
    assertEquals(expected, HttpRequests.encodeURIComponent(str));
  }
}
