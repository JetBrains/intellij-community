// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import kotlin.test.assertEquals

@TestApplication
class IdeProxySelectorTest {
  @Test
  fun testIdeProxySelector() {
    var configuration: ProxyConfiguration = ProxyConfiguration.direct
    val selector = IdeProxySelector { configuration }

    assertEquals(NO_PROXY_LIST, selector.select(URI.create("https://example.com")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://192.168.0.1")))

    configuration = ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "domain.com", 500, "192.168.*, sub.example.com")
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://192.168.0.1")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("https://sub.example.com")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://sub.example.com")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://localhost")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://127.0.0.1")))
    assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500))), selector.select(URI.create("https://example.com")))
    assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500))), selector.select(URI.create("http://8.8.8.8")))

    configuration = ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.SOCKS, "domain.com", 500, "192.168.*, sub.example.com")
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://192.168.0.1")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("https://sub.example.com")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://sub.example.com")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://localhost")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://127.0.0.1")))
    assertEquals(listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("domain.com", 500))), selector.select(URI.create("https://example.com")))
    assertEquals(listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("domain.com", 500))), selector.select(URI.create("http://8.8.8.8")))

    configuration = ProxyConfiguration.proxyAutoConfiguration(
      Path.of(PlatformTestUtil.getPlatformTestDataPath(), "proxy", "ide_proxy_selector.pac").toUri().toURL()
    )
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://192.168.0.1")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("https://something.org")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://localhost")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://127.0.0.1")))
    assertEquals(listOf(
      Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.domain.com", 3129)),
      Proxy.NO_PROXY
    ), selector.select(URI.create("https://example.com/something")))
    assertEquals(listOf(
      Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("p1.domain.com", 3129)),
      Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("p2.domain.com", 9999)),
      Proxy.NO_PROXY
    ), selector.select(URI.create("https://other.example.com/something")))

    // check that URL sanitization works
    assertEquals(listOf(
      Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("p1.domain.com", 3129)),
    ), selector.select(URI.create("https://sub.example-domain.com")))
    assertEquals(listOf(
      Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("p1.domain.com", 3129)),
    ), selector.select(URI.create("https://sub.example-domain.com/somepath?query=1")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("https://example-domain.com")))

    // make sure broken pac does not destroy the IDE
    configuration = ProxyConfiguration.proxyAutoConfiguration(
      Path.of(PlatformTestUtil.getPlatformTestDataPath(), "proxy", "broken.pac").toUri().toURL()
    )
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://192.168.0.1")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("http://example.com")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("https://example.com/")))
    assertEquals(NO_PROXY_LIST, selector.select(URI.create("https://example.com/something")))
  }
}