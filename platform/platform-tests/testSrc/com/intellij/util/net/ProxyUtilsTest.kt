// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol.HTTP
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol.SOCKS
import com.intellij.util.proxy.JavaProxyProperty.HTTPS_HOST
import com.intellij.util.proxy.JavaProxyProperty.HTTPS_PORT
import com.intellij.util.proxy.JavaProxyProperty.HTTPS_PROXY_PASSWORD
import com.intellij.util.proxy.JavaProxyProperty.HTTPS_PROXY_USER
import com.intellij.util.proxy.JavaProxyProperty.HTTP_HOST
import com.intellij.util.proxy.JavaProxyProperty.HTTP_NON_PROXY_HOSTS
import com.intellij.util.proxy.JavaProxyProperty.HTTP_PORT
import com.intellij.util.proxy.JavaProxyProperty.HTTP_PROXY_PASSWORD
import com.intellij.util.proxy.JavaProxyProperty.HTTP_PROXY_USER
import com.intellij.util.proxy.JavaProxyProperty.SOCKS_HOST
import com.intellij.util.proxy.JavaProxyProperty.SOCKS_PASSWORD
import com.intellij.util.proxy.JavaProxyProperty.SOCKS_PORT
import com.intellij.util.proxy.JavaProxyProperty.SOCKS_USERNAME
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import kotlin.test.assertEquals

class ProxyUtilsTest {
  private val userCredentials = Credentials("user", "pwd")

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
      getJvmProperties(ProxyConfiguration.proxy(HTTP, "domain.com", 500, "127.0.0.1, *.example.com"), credentials = null)
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
      getJvmProperties(ProxyConfiguration.proxy(HTTP, "domain.com", 500, "127.0.0.1, *.example.com"), userCredentials)
    )

    assertEquals(
      mapOf(
        SOCKS_HOST to "domain.com",
        SOCKS_PORT to "500",
        HTTP_NON_PROXY_HOSTS to "127.0.0.1",
      ),
      getJvmProperties(ProxyConfiguration.proxy(SOCKS, "domain.com", 500, "127.0.0.1"), credentials = null)
    )
    assertEquals(
      mapOf(
        SOCKS_HOST to "domain.com",
        SOCKS_PORT to "500",
        HTTP_NON_PROXY_HOSTS to "127.0.0.1",
        SOCKS_USERNAME to "user",
        SOCKS_PASSWORD to "pwd"
      ),
      getJvmProperties(ProxyConfiguration.proxy(SOCKS, "domain.com", 500, "127.0.0.1"), userCredentials)
    )
  }

  @Test
  fun testGetApplicableProxiesAsJvmProperties() {
    val proxySelector = object : ProxySelector() {
      override fun select(uri: URI?): List<Proxy?> =
        when (uri) {
          URI.create("https://example.com/path") -> listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("domain.com", 500)))
          URI.create("http://sub.example.com/") -> listOf(
            Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("p1.domain.com", 500)),
            Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("p2.domain.com", 501))
          )
          else -> NO_PROXY_LIST
        }

      override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }
    val credentialProvider = { host: String, port: Int ->
      userCredentials.takeIf { host.endsWith("domain.com") && port == 500 }
    }

    assertEquals(
      emptyList(),
      getDetectedSettingsAsJvmProperties(URI.create("domain.com"), proxySelector, credentialProvider)
    )
    assertEquals(
      emptyList(),
      getDetectedSettingsAsJvmProperties(URI.create("https://example.com"), proxySelector, credentialProvider)
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
      getDetectedSettingsAsJvmProperties(URI.create("https://example.com/path"), proxySelector, credentialProvider)
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
      getDetectedSettingsAsJvmProperties(URI.create("http://sub.example.com/"), proxySelector, credentialProvider)
    )
  }
}
