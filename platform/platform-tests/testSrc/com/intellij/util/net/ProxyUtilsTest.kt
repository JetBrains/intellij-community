// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol.*
import com.intellij.util.proxy.JavaProxyProperty.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlin.test.assertEquals

class ProxyUtilsTest {
  val userCredentialProvider = ProxyCredentialProvider { host, port -> Credentials("user", "pwd").takeIf { host.endsWith("domain.com") && port == 500 } }

  @Test
  fun testJdkProxyAsJvmProperties() {
    assertEquals(emptyMap(), Proxy.NO_PROXY.asJvmProperties())
    assertEquals(
      mapOf(
        HTTP_HOST to "domain.com",
        HTTP_PORT to "443",
        HTTPS_HOST to "domain.com",
        HTTPS_PORT to "443",
      ),
      Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 443)).asJvmProperties()
    )
    assertEquals(
      mapOf(
        SOCKS_HOST to "domain.com",
        SOCKS_PORT to "443",
      ),
      Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("domain.com", 443)).asJvmProperties()
    )
  }

  @Test
  fun testStaticProxyConfigurationAsJavaProxy() {
    assertEquals(
      Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500)),
      ProxyConfiguration.proxy(HTTP, "domain.com", 500, "127.0.0.1").asJavaProxy()
    )
    assertEquals(
      Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("domain.com", 500)),
      ProxyConfiguration.proxy(SOCKS, "domain.com", 500, "127.0.0.1").asJavaProxy()
    )
  }

  @Test
  fun testStaticProxyConfigurationAsJvmProperties() {
    assertEquals(
      mapOf(
        HTTP_HOST to "domain.com",
        HTTP_PORT to "500",
        HTTPS_HOST to "domain.com",
        HTTPS_PORT to "500",
        HTTP_NON_PROXY_HOSTS to "127.0.0.1|*.example.com",
      ),
      ProxyConfiguration.proxy(HTTP, "domain.com", 500, "127.0.0.1, *.example.com").asJvmProperties(null)
    )
    assertEquals(
      mapOf(
        HTTP_HOST to "domain.com",
        HTTP_PORT to "500",
        HTTP_PROXY_USER to "user",
        HTTP_PROXY_PASSWORD to "pwd",
        HTTP_NON_PROXY_HOSTS to "127.0.0.1|*.example.com",
        HTTPS_HOST to "domain.com",
        HTTPS_PORT to "500",
        HTTPS_PROXY_USER to "user",
        HTTPS_PROXY_PASSWORD to "pwd",
      ),
      ProxyConfiguration.proxy(HTTP, "domain.com", 500, "127.0.0.1, *.example.com").asJvmProperties(userCredentialProvider)
    )

    assertEquals(
      mapOf(
        SOCKS_HOST to "domain.com",
        SOCKS_PORT to "500",
        HTTP_NON_PROXY_HOSTS to "127.0.0.1",
      ),
      ProxyConfiguration.proxy(SOCKS, "domain.com", 500, "127.0.0.1").asJvmProperties(null)
    )
    assertEquals(
      mapOf(
        SOCKS_HOST to "domain.com",
        SOCKS_PORT to "500",
        HTTP_NON_PROXY_HOSTS to "127.0.0.1",
        SOCKS_USERNAME to "user",
        SOCKS_PASSWORD to "pwd"
      ),
      ProxyConfiguration.proxy(SOCKS, "domain.com", 500, "127.0.0.1").asJvmProperties(userCredentialProvider)
    )
  }

  @Test
  fun testGetApplicableProxiesAsJvmProperties() {
    val proxySelector = object : ProxySelector() {
      override fun select(uri: URI?): List<Proxy?> =
        when (uri) {
          URI.create("https://example.com/path") -> listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500)))
          URI.create("http://sub.example.com/") -> listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("p1.domain.com", 500)),
                                                          Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("p2.domain.com", 501)))
          else -> NO_PROXY_LIST
        }

      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }

    assertEquals(
      emptyList(),
      URI.create("domain.com").getApplicableProxiesAsJvmProperties(userCredentialProvider, proxySelector)
    )
    assertEquals(
      emptyList(),
      URI.create("https://example.com").getApplicableProxiesAsJvmProperties(userCredentialProvider, proxySelector)
    )
    assertEquals(
      listOf(
        mapOf(
          HTTP_HOST to "domain.com",
          HTTP_PORT to "500",
          HTTP_PROXY_USER to "user",
          HTTP_PROXY_PASSWORD to "pwd",
          HTTPS_HOST to "domain.com",
          HTTPS_PORT to "500",
          HTTPS_PROXY_USER to "user",
          HTTPS_PROXY_PASSWORD to "pwd",
        )
      ),
      URI.create("https://example.com/path").getApplicableProxiesAsJvmProperties(userCredentialProvider, proxySelector)
    )
    assertEquals(
      listOf(
        mapOf(
          HTTP_HOST to "p1.domain.com",
          HTTP_PORT to "500",
          HTTP_PROXY_USER to "user",
          HTTP_PROXY_PASSWORD to "pwd",
          HTTPS_HOST to "p1.domain.com",
          HTTPS_PORT to "500",
          HTTPS_PROXY_USER to "user",
          HTTPS_PROXY_PASSWORD to "pwd",
        ),
        mapOf(
          SOCKS_HOST to "p2.domain.com",
          SOCKS_PORT to "501",
        )
      ),
      URI.create("http://sub.example.com/").getApplicableProxiesAsJvmProperties(userCredentialProvider, proxySelector)
    )
  }
}