// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection

import com.intellij.internal.statistic.eventLog.connection.request.StatsProxyInfo
import java.net.Proxy
import javax.net.ssl.SSLContext

private val NO_PROXY: StatsProxyInfo = StatsProxyInfo(Proxy.NO_PROXY, null)

interface EventLogConnectionSettings {
  fun getUserAgent(): String

  fun selectProxy(url: String): StatsProxyInfo

  fun getSSLContext(): SSLContext?

  fun getExtraHeaders(): Map<String, String>
}

class EventLogBasicConnectionSettings(private val userAgent: String, private val extraHeaders: Map<String, String> = emptyMap()) : EventLogConnectionSettings {
  override fun getUserAgent(): String = userAgent
  override fun selectProxy(url: String): StatsProxyInfo = NO_PROXY
  override fun getSSLContext(): SSLContext? = null
  override fun getExtraHeaders(): Map<String, String> = extraHeaders
}
