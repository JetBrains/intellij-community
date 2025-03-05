// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.junit5.TestApplication
import io.ktor.server.cio.HttpServer
import io.ktor.server.cio.HttpServerSettings
import io.ktor.server.cio.backend.httpServer
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.Authenticator
import kotlin.test.assertEquals

@TestApplication
class PlatformProxyAuthTest {
  @Test
  fun testJdkAuthenticatorIsOverridden() {
    assertEquals(Authenticator.getDefault(), JdkProxyProvider.getInstance().authenticator)
  }

  @Test
  fun testHttpConnectionThroughProxyWithAuthentication(): Unit = runBlocking {
    val serverScope = childScope("embedded http proxy server", Dispatchers.IO)
    val proxyServer = serverScope.embeddedHttpProxyServer(3141)
    proxyServer.serverSocket.await()
    //launchTestHttpWebServer(3142)
    withProxyConfiguration(ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "0.0.0.0", 3141)) {
      withKnownProxyCredentials("0.0.0.0", 3141, null) {
        val conn = HttpConnectionUtils.openHttpConnection("http://0.0.0.0:3142/path")
        conn.setAuthenticator(createResetPlatformAuthenticator())
        assertEquals(407, conn.responseCode)
        conn.disconnect()
      }
      withKnownProxyCredentials("0.0.0.0", 3141, Credentials("myuser", "mypassword")) {
        val conn = HttpConnectionUtils.openHttpConnection("http://0.0.0.0:3142/path")
        conn.setAuthenticator(createResetPlatformAuthenticator())
        assertEquals(200, conn.responseCode)
        conn.disconnect()
      }
      withKnownProxyCredentials("0.0.0.0", 3141, Credentials("myuser", "wrongpassword")) {
        val conn = HttpConnectionUtils.openHttpConnection("http://0.0.0.0:3142/path")
        conn.setAuthenticator(createResetPlatformAuthenticator())
        assertEquals(407, conn.responseCode)
        conn.disconnect()
      }
    }
    serverScope.cancel()
  }
}

/**
 * Based on recorded squid proxy interaction
 */
private fun CoroutineScope.embeddedHttpProxyServer(
  port: Int,
): HttpServer {
  return httpServer(HttpServerSettings(port = port)) { req ->
    assertEquals("http://0.0.0.0:3142/path", req.uri.toString())
    val resp = if (req.headers["Proxy-authorization"] != "Basic bXl1c2VyOm15cGFzc3dvcmQ=") {
      // content cut out
      """
      HTTP/1.1 407 Proxy Authentication Required
      Server: squid/6.9
      Mime-Version: 1.0
      Date: Mon, 03 Jun 2024 10:10:42 GMT
      Content-Type: text/html;charset=utf-8
      Content-Length: 0
      X-Squid-Error: ERR_CACHE_ACCESS_DENIED 0
      Vary: Accept-Language
      Content-Language: en
      Proxy-Authenticate: Basic realm="Squid proxy-caching web server"
      Cache-Status: localhost
      Via: 1.1 localhost (squid/6.9)
      Connection: close
      
      
      """.trimIndent()
    } else {
      """
      HTTP/1.1 200 OK
      Content-Length: 13
      Content-Type: text/plain; charset=UTF-8
      Date: Mon, 03 Jun 2024 10:10:42 GMT
      Cache-Status: localhost;fwd=stale;detail=match
      Via: 1.1 localhost (squid/6.9)
      Connection: close
      
      Hello, World!""".trimIndent()
    }.replace("\n", "\r\n").toByteArray()
    output.writeFully(resp, 0, resp.size)
    output.flush()
    req.close()
  }
}

/*
private fun CoroutineScope.launchTestHttpWebServer(port: Int): Job {
  return launch(CoroutineName("embedded http web server") + Dispatchers.IO) {
    embeddedServer(CIO, port) {
      routing {
        get("/path") {
          call.respondText("Hello, World!")
        }
      }
    }.start()
  }
}
*/
