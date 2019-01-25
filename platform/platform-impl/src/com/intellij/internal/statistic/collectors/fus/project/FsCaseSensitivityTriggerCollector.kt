// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.project

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.utils.createData
import com.intellij.openapi.project.Project

object FsCaseSensitivityTriggerCollector {
  private val groupId = FeatureUsageGroup("statistics.project.root.fs.case", 1)
  private val context = FUSUsageContext.create(FUSUsageContext.getOSNameContextData(), systemFsContextData())

  @JvmStatic
  fun trigger(project: Project, sensitivity: Boolean) {
    FeatureUsageLogger.log(groupId, sensitivity.encode(), createData(project, context))
  }

  private fun systemFsContextData() = "system:" + (System.getProperty("idea.case.sensitive.fs")?.toBoolean()?.encode() ?: "default")
  private fun Boolean.encode() = if (this) "case-sensitive" else "case-insensitive"
}