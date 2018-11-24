/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.network;

import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IdeaWideProxySelector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class ProxyExceptionsTest {
  private HttpConfigurable myConfigurable;
  private IdeaWideProxySelector mySelector;

  @Before
  public void setUp() {
    myConfigurable = new HttpConfigurable();
    myConfigurable.USE_HTTP_PROXY = true;
    myConfigurable.PROXY_TYPE_IS_SOCKS = false;
    myConfigurable.PROXY_HOST = "proxy";
    myConfigurable.PROXY_PORT = 1234;

    mySelector = new IdeaWideProxySelector(myConfigurable);
  }

  @Test
  public void testSimple() throws Exception {
    //65.55.58.201
    myConfigurable.PROXY_EXCEPTIONS = "*.myhost.com, 65.55.58.*";

    assertProxied("http://myhost.com");
    assertDirect("http://somewhere.myhost.com/inner/url");
    assertDirect("https://somewhere.myhost.com:4567/inner/url");
    assertDirect("http://65.55.58.201:4567/inner/url");

    assertProxied("http://65.55.59.201:4567/inner/url");
    assertProxied("http://jetbrains.com");
  }

  private void assertDirect(final String s) throws URISyntaxException {
    List<Proxy> proxies = mySelector.select(new URI(s));
    Assert.assertTrue(proxies.size() == 1);
    Assert.assertEquals(Proxy.Type.DIRECT, proxies.get(0).type());
  }

  private void assertProxied(final String s) throws URISyntaxException {
    List<Proxy> proxies = mySelector.select(new URI(s));
    Assert.assertTrue(proxies.size() == 1);
    Assert.assertEquals(Proxy.Type.HTTP, proxies.get(0).type());
    Assert.assertEquals(myConfigurable.PROXY_HOST, ((InetSocketAddress) proxies.get(0).address()).getHostName());
  }
}
