// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.openapi.project.Project

interface TerminalFusAwareHandler {
  /**
   * Fill data to be sent to statistic collector server
   */
  fun fillData(project: Project, workingDirectory: String?, localSession: Boolean, command: String, data: FeatureUsageData)
}