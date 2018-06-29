// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

interface LogEventFilter {
  fun accepts(groupId: String) : Boolean
}

class LogEventWhitelistFilter(val whitelist: Set<String>) : LogEventFilter {
  override fun accepts(groupId: String): Boolean {
    return whitelist.contains(groupId)
  }
}

object LogEventTrueFilter : LogEventFilter {
  override fun accepts(groupId: String): Boolean {
    return true
  }
}