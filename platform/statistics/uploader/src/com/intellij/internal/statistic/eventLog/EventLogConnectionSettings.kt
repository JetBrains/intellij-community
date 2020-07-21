// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import java.net.Proxy

interface EventLogConnectionSettings {
  fun getUserAgent(): String

  fun selectProxy(url: String): Proxy
}

class EventLogBasicConnectionSettings(private val userAgent: String) : EventLogConnectionSettings {
  override fun getUserAgent(): String = userAgent
  override fun selectProxy(url: String): Proxy = Proxy.NO_PROXY
}
