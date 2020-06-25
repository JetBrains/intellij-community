// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl

import com.intellij.internal.statistic.eventLog.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.rules.PsiElementUsage

class UsageViewStatisticsCollector : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    val GROUP = EventLogGroup("usage.view", 1)

    val referenceClass = EventFields.Class("reference_class")
    val usageShown = GROUP.registerEvent("usage.shown", referenceClass, EventFields.Language)
    val usageNavigate = GROUP.registerEvent("usage.navigate", referenceClass, EventFields.Language)

    @JvmStatic
    fun logUsageShown(project: Project?, referenceClass: Class<out Any>?, language: Language?) {
      usageShown.log(project, referenceClass, language)
    }

    @JvmStatic
    fun logUsageNavigate(project: Project?, usage: Usage) {
      usageNavigate.log(project, (usage as? PsiElementUsage)?.referenceClass, (usage as? PsiElementUsage)?.element?.language)
    }

    @JvmStatic
    fun logUsageNavigate(project: Project?, usage: UsageInfo) {
      usageNavigate.log(project, usage.referenceClass, usage.element?.language)
    }
  }
}
