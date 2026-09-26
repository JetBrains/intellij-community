// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Allows products with separate backends, which also support FUS (like Rider), to output their logs in the statistics event log tool window
 */
interface StatisticsEventLogToolWindowEPLogProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<StatisticsEventLogToolWindowEPLogProvider> =
      ExtensionPointName.create("com.intellij.internal.statistic.extensibility.eventLogToolWindowEPLogProvider")
  }

  fun subscribe(recorderId: String, function: (String) -> Unit)
  fun unsubscribe(recorderId: String)
  fun init(project: Project)
}
