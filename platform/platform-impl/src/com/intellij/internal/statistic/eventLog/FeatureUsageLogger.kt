// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.util.registry.Registry

object FeatureUsageLogger {
  private val ourLogger = if (isEnabled()) FeatureUsageFileEventLogger() else FeatureUsageEmptyEventLogger()

  fun log(recorderId: String, action: String) {
    return ourLogger.log(recorderId, action)
  }

  fun isEnabled() : Boolean {
    return Registry.`is`("feature.usage.event.log.collect.and.upload")
  }
}

interface FeatureUsageEventLogger {
  fun log(recorderId: String, action: String)
}

class FeatureUsageEmptyEventLogger : FeatureUsageEventLogger {
  override fun log(recorderId: String, action: String) {
  }
}