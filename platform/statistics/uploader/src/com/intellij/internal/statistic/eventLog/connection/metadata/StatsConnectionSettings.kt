// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection.metadata

import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpRequests
import com.jetbrains.fus.reporting.FusHttpClient
import com.jetbrains.fus.reporting.jvm.JvmHttpClient
import com.jetbrains.fus.reporting.jvm.ProxyInfo
import java.net.Proxy
import javax.net.ssl.SSLContext

private val NO_PROXY: StatsProxyInfo = StatsProxyInfo(Proxy.NO_PROXY, null)

interface StatsConnectionSettings {
  fun provideUserAgent(): String
  fun provideProxy(url: String): StatsProxyInfo
  fun provideSSLContext(): SSLContext?
  fun provideExtraHeaders(): Map<String, String>
}

class StatsBasicConnectionSettings(
  private val userAgent: String,
  private val extraHeaders: Map<String, String> = emptyMap(),
  private val sslContext: SSLContext? = null,
  private val proxy: StatsProxyInfo = NO_PROXY
): StatsConnectionSettings {
  override fun provideUserAgent(): String = userAgent
  override fun provideExtraHeaders(): Map<String, String> = extraHeaders
  override fun provideSSLContext(): SSLContext? = sslContext
  override fun provideProxy(url: String): StatsProxyInfo = proxy
}

class StatsProxyInfo(val proxy: Proxy, val proxyAuth: StatsProxyAuthProvider? = null) {
  val isNoProxy: Boolean = proxy === Proxy.NO_PROXY

  interface StatsProxyAuthProvider {
    val proxyLogin: String?
    val proxyPassword: String?
  }
}

fun StatsConnectionSettings.createJvmHttpClient(): FusHttpClient {
  val client = JvmHttpClient(
    sslContextProvider = { provideSSLContext() },
    proxyProvider = { configurationUrl ->
      val statsProxy = provideProxy(configurationUrl)
      ProxyInfo(statsProxy.proxy, statsProxy.proxyAuth?.let { auth ->
        object : ProxyInfo.ProxyAuthProvider {
          override val proxyLogin: String? = auth.proxyLogin
          override val proxyPassword: String? = auth.proxyPassword
        }
      })
    },
    extraHeadersProvider = { provideExtraHeaders() },
    userAgent = provideUserAgent()
  )
  return EnglishLastModifiedHttpClient(client, this)
}

/**
 * Reads `Last-Modified` through [StatsHttpRequests], which parses it in English.
 * [JvmHttpClient] parses it with the default locale and returns 0 when that locale is not English.
 */
private class EnglishLastModifiedHttpClient(
  private val delegate: FusHttpClient,
  private val settings: StatsConnectionSettings,
) : FusHttpClient by delegate {
  override fun lastModified(url: String): Long =
    StatsHttpRequests.head(url, settings).send { it.lastModified() }.result ?: 0L
}
