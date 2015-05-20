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
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

public class HttpRequestsTest  {
  private final HttpRequests.RequestProcessor<Void> myProcessor = new HttpRequests.RequestProcessor<Void>() {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public Void process(@NotNull HttpRequests.Request request) throws IOException {
      request.getInputStream().read();
      return null;
    }
  };

  @Before
  public void setUp() throws Exception {
    InetAddress addr = InetAddress.getByName("openjdk.java.net");
    assumeThat(addr, instanceOf(Inet4Address.class));
  }

  @Test
  public void testLimit() {
    try {
      HttpRequests.request("").redirectLimit(0).connect(myProcessor);
      fail();
    }
    catch (IOException e) {
      assertEquals(IdeBundle.message("error.connection.failed.redirects"), e.getMessage());
    }
  }

  @Test(timeout = 5000, expected = SocketTimeoutException.class)
  public void testConnectTimeout() throws IOException {
    HttpRequests.request("http://openjdk.java.net").connectTimeout(1).connect(myProcessor);
    fail();
  }

  @Test(timeout = 5000, expected = SocketTimeoutException.class)
  public void testReadTimeout() throws IOException {
    HttpRequests.request("http://openjdk.java.net").readTimeout(1).connect(myProcessor);
    fail();
  }

  @Test(timeout = 5000)
  public void testReadString() throws IOException {
    assertThat(HttpRequests.request("http://openjdk.java.net").readString(null), containsString("Download"));
  }
}
