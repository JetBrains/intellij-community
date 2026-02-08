package com.intellij.grazie.cloud

import ai.grazie.client.common.SuspendableHTTPClient
import ai.grazie.client.ktor.GrazieKtorHTTPClient
import ai.grazie.utils.http.DefaultHttpClientRequestSetup
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.net.HttpConfigurable
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
  private val headerLogger =
    com.intellij.openapi.diagnostic.Logger.getInstance("com.intellij.ml.grazie.pro.http.headers")

  private var lastClient: Pair<Proxy?, HttpClient>? = null

  private val client: HttpClient
    @Synchronized get() {
      val proxyConfig = HttpConfigurable.getInstance()
      val proxy = if (proxyConfig.USE_HTTP_PROXY) Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyConfig.PROXY_HOST, proxyConfig.PROXY_PORT)) else null
      var last = lastClient
      if (last == null || last.first != proxy) {
        thisLogger().debug("Using proxy $proxy")
        last = Pair(proxy, createKtorClient(proxy))
        lastClient = last
      }
      return last.second
    }

  private fun createKtorClient(jdkProxy: Proxy?) = HttpClient(CIO) {
    val proxyConfig = HttpConfigurable.getInstance()
    if (proxyConfig.USE_HTTP_PROXY) {
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
      val proxyConfig = HttpConfigurable.getInstance()
      if (proxyConfig.USE_HTTP_PROXY && proxyConfig.PROXY_AUTHENTICATION) {
        val proxyLogin = proxyConfig.proxyLogin
        val proxyPassword = proxyConfig.plainProxyPassword
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
  }
}