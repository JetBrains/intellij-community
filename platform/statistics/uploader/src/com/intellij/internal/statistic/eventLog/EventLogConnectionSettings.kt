// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.service.request.StatsProxyInfo
import java.net.Proxy

private val NO_PROXY: StatsProxyInfo = StatsProxyInfo(Proxy.NO_PROXY, null)

interface EventLogConnectionSettings {
  fun getUserAgent(): String

  fun selectProxy(url: String): StatsProxyInfo
}

class EventLogBasicConnectionSettings(private val userAgent: String) : EventLogConnectionSettings {
  override fun getUserAgent(): String = userAgent
  override fun selectProxy(url: String): StatsProxyInfo = NO_PROXY
}
