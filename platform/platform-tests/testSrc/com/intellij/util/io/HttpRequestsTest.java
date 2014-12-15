/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class HttpRequestsTest {
  private final HttpRequests.RequestProcessor<Void> myProcessor = new HttpRequests.RequestProcessor<Void>() {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public Void process(@NotNull HttpRequests.Request request) throws IOException {
      request.getInputStream().read();
      return null;
    }
  };

  @Test
  public void testLimit() {
    try {
      HttpRequests.request("").redirectLimit(0).connect(myProcessor);
      fail();
    }
    catch (IOException e) {
      assertEquals("Too many redirects", e.getMessage());
    }
  }

  @Test
  public void testConnectTimeout() {
    try {
      HttpRequests.request("http://openjdk.java.net").connectTimeout(1).connect(myProcessor);
      fail();
    }
    catch (SocketTimeoutException ignore) { }
    catch (IOException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testReadTimeout() {
    try {
      HttpRequests.request("http://openjdk.java.net").readTimeout(1).connect(myProcessor);
      fail();
    }
    catch (SocketTimeoutException ignore) { }
    catch (IOException e) {
      fail(e.getMessage());
    }
  }
}
