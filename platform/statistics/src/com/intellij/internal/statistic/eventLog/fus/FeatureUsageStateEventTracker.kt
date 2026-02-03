// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface FeatureUsageStateEventTracker {
  fun initialize()

  suspend fun reportNow()

  companion object {
    val EP_NAME: ExtensionPointName<FeatureUsageStateEventTracker> = ExtensionPointName("com.intellij.statistic.eventLog.fusStateEventTracker")
  }
}

internal fun initStateEventTrackers() {
  FeatureUsageStateEventTracker.EP_NAME.forEachExtensionSafe { it.initialize() }
}