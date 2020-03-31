// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.openapi.extensions.ExtensionPointName

interface FeatureUsageStateEventTracker {
  fun initialize()
  fun reportNow()

  companion object {
    val EP_NAME = ExtensionPointName<FeatureUsageStateEventTracker>("com.intellij.statistic.eventLog.fusStateEventTracker")
  }
}

internal fun initStateEventTrackers() {
  FeatureUsageStateEventTracker.EP_NAME.forEachExtensionSafe { it.initialize() }
}