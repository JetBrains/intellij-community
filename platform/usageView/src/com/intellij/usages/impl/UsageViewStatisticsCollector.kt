// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl

import com.intellij.internal.statistic.eventLog.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage

class UsageViewStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    val GROUP = EventLogGroup("usage.view", 1)

    val referenceClass = EventFields.Class("reference_class")
    val usageShown = GROUP.registerEvent("usage.shown", referenceClass, EventFields.Language)

    @JvmStatic
    fun logUsageShown(project: Project?, usage: Usage) {
      usageShown.log(project, (usage as? PsiElementUsage)?.referenceClass, (usage as? PsiElementUsage)?.element?.language)
    }
  }
}
