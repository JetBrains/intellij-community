package com.intellij.grazie.cloud

import ai.grazie.client.common.SuspendableHTTPClient
import ai.grazie.client.ktor.GrazieKtorHTTPClient
import ai.grazie.utils.http.DefaultHttpClientRequestSetup
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/** NB: HTTP client instances should not be cached in the callers. */
@Service(Service.Level.APP)
internal class GrazieHttpClientManager : Disposable {
  @Suppress("SSBasedInspection")
  private val headerLogger =
    com.intellij.openapi.diagnostic.Logger.getInstance("com.intellij.ml.grazie.pro.http.headers")

  private var lastClient: Pair<Proxy?, HttpClient>? = null

  private val client: HttpClient
    @Synchronized get() {
      val proxyConfig = ProxySettings.getInstance().getProxyConfiguration()
      val proxy = if (proxyConfig is ProxyConfiguration.StaticProxyConfiguration)
        Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.host, proxyConfig.port)) else null
      var last = lastClient
      if (last == null || last.first != proxy) {
        last?.second?.close()
        thisLogger().debug("Using proxy $proxy")
        last = Pair(proxy, createKtorClient(proxy))
        lastClient = last
      }
      return last.second
    }

  private fun createKtorClient(jdkProxy: Proxy?) = HttpClient(CIO) {
    val proxyConfig = ProxySettings.getInstance().getProxyConfiguration()
    if (proxyConfig is ProxyConfiguration.StaticProxyConfiguration) {
      engine { proxy = jdkProxy }
    }
    install(Logging) {
      logger = object: Logger {
        override fun log(message: String) {
          headerLogger.debug(message)
        }
      }
      level = LogLevel.HEADERS
    }
    install(HttpTimeout) {
      val timeoutMs = 5.seconds.inWholeMilliseconds
      connectTimeoutMillis = timeoutMs
      requestTimeoutMillis = timeoutMs
      socketTimeoutMillis = timeoutMs
    }
  }

  val instance: SuspendableHTTPClient
    get() = prepare(client)

  val withExtendedTimeout: GrazieHttpClientAdaptor
    get() = prepare(client.config {
      install(HttpTimeout) {
        requestTimeoutMillis = 30.seconds.inWholeMilliseconds
      }
    })

  private fun prepare(client: HttpClient): GrazieHttpClientAdaptor {
    val withProxy = client.config {
      val credentials = ProxyCredentialStore.getInstance().getCredentials(ProxySettings.getInstance().getProxyConfiguration())
      if (credentials != null) {
        val proxyLogin = credentials.userName
        val proxyPassword = credentials.getPasswordAsString()
        thisLogger().debug("Using proxy authentication $proxyLogin ${proxyPassword?.length}")
        val token = Base64.getEncoder().encodeToString("$proxyLogin:$proxyPassword".toByteArray())
        defaultRequest {
          header(HttpHeaders.ProxyAuthorization, "Basic $token")
        }
      }
    }
    return GrazieHttpClientAdaptor(GrazieKtorHTTPClient(withProxy, DefaultHttpClientRequestSetup))
  }

  override fun dispose() {
    lastClient?.second?.close()
    lastClient = null
  }
}
