// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.*
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class PlatformProxySelectorTest {
  @Test
  fun testJdkProxySelectorIsOverridden() {
    assertEquals(ProxySelector.getDefault(), JdkProxyProvider.getInstance().proxySelector)
  }

  @Test
  fun testJdkProxyHonorsProxySettings() {
    val proxySelector = JdkProxyProvider.getInstance().proxySelector
    withProxyConfiguration(ProxyConfiguration.direct) {
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://example.com")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://8.8.8.8")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://localhost")))
    }
    withProxyConfiguration(ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "domain.com", 500, "sub.example.com")) {
      val proxyList = listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500)))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://localhost")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))
      assertEquals(proxyList, proxySelector.select(URI.create("http://8.8.8.8")))
      assertEquals(proxyList, proxySelector.select(URI.create("http://example.com")))
    }
    withProxyConfiguration(ProxyConfiguration.proxyAutoConfiguration(
      Path.of(PlatformTestUtil.getPlatformTestDataPath(), "proxy", "ide_proxy_selector.pac").toUri().toURL()
    )) {
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://localhost")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://8.8.8.8")))
      assertEquals(listOf(
        Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.domain.com", 3129)),
        Proxy.NO_PROXY
      ), proxySelector.select(URI.create("https://example.com/something")))
      assertEquals(listOf(
        Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("p1.domain.com", 3129)),
        Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("p2.domain.com", 9999)),
        Proxy.NO_PROXY
      ), proxySelector.select(URI.create("https://other.example.com/something")))
    }
  }

  @Test
  fun testJdkProxyCustomization() {
    val proxySelector = JdkProxyProvider.getInstance().proxySelector
    withProxyConfiguration(ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "domain.com", 500)) {
      assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500))), proxySelector.select(URI.create("http://example.com")))
      assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500))), proxySelector.select(URI.create("http://sub.example.com")))
      withCustomProxySelector(object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy?>? {
          return when (uri) {
            URI.create("http://sub.example.com") -> listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("p1.domain.com", 502)))
            URI.create("http://noproxy.example.com") -> NO_PROXY_LIST
            else -> emptyList()
          }
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
      }) {
        assertEquals(listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("p1.domain.com", 502))), proxySelector.select(URI.create("http://sub.example.com")))
        assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://noproxy.example.com")))
        assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500))), proxySelector.select(URI.create("http://example.com")))
      }
      assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500))), proxySelector.select(URI.create("http://example.com")))
      assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500))), proxySelector.select(URI.create("http://sub.example.com")))
    }
  }

  @Test
  fun testProxySettingsOverride() {
    val proxySelector = JdkProxyProvider.getInstance().proxySelector
    withProxyConfiguration(ProxyConfiguration.direct) {
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://example.com")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))
      assertFalse(ProxySettingsOverrideProvider.areProxySettingsOverridden())
      withProxySettingsOverride(object : ProxySettingsOverrideProvider {
        override val shouldUserSettingsBeOverriden: Boolean = true
        override val proxyConfigurationProvider: ProxyConfigurationProvider = ProxyConfigurationProvider {
          ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "another.domain.com", 502, "sub.example.com")
        }
      }) {
        assertTrue(ProxySettingsOverrideProvider.areProxySettingsOverridden())
        assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("another.domain.com", 502))), proxySelector.select(URI.create("http://example.com")))
        assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))
      }
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://example.com")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))

      withProxySettingsOverride(object : ProxySettingsOverrideProvider {
        override val shouldUserSettingsBeOverriden: Boolean = false // no override
        override val proxyConfigurationProvider: ProxyConfigurationProvider = ProxyConfigurationProvider {
          ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "another.domain.com", 502, "sub.example.com")
        }
      }) {
        assertFalse(ProxySettingsOverrideProvider.areProxySettingsOverridden())
        assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://example.com")))
        assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))
      }
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://example.com")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))

      withProxySettingsOverride(object : ProxySettingsOverrideProvider {
        override val shouldUserSettingsBeOverriden: Boolean = true
        override val proxyConfigurationProvider: ProxyConfigurationProvider = ProxyConfigurationProvider {
          ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "another.domain.com", 502, "sub.example.com")
        }
      }) {
        withProxySettingsOverride(object : ProxySettingsOverrideProvider {
          override val shouldUserSettingsBeOverriden: Boolean = true
          override val proxyConfigurationProvider: ProxyConfigurationProvider = ProxyConfigurationProvider {
            ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "second.domain.com", 503, "sub.example.com")
          }
        }) {
          // only the first override is effective
          assertEquals(listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("another.domain.com", 502))), proxySelector.select(URI.create("http://example.com")))
          assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))
          assertTrue(ProxySettingsOverrideProvider.areProxySettingsOverridden())
        }
      }
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://example.com")))
      assertEquals(NO_PROXY_LIST, proxySelector.select(URI.create("http://sub.example.com")))
    }
  }

  // IJPL-161812
  @Test
  fun testSelectNull() {
    val proxySelector = JdkProxyProvider.getInstance().proxySelector
    assertContentEquals(proxySelector.select(null), NO_PROXY_LIST)
  }
}