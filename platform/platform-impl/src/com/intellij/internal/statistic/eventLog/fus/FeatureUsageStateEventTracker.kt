// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.openapi.extensions.ExtensionPointName

private val EP_NAME = ExtensionPointName.create<FeatureUsageStateEventTracker>("com.intellij.statistic.eventLog.fusStateEventTracker")

interface FeatureUsageStateEventTracker {
  fun initialize()
}

fun initStateEventTrackers() {
  for (extension in EP_NAME.extensions) {
    extension.initialize()
  }
}