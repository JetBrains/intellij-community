// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.usages.UsageView
import com.intellij.usages.impl.UsageViewStatisticsCollector.Companion.USAGE_VIEW

class ShowUsagesMetricsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  companion object {
    private val GROUP = EventLogGroup("show.usages.metrics", 1)
    private val SELECTED_USAGE = EventFields.Int("selected_usage")
    private val NUMBER_OF_USAGES = EventFields.Int("number_of_usages")
    private val NUMBER_OF_LETTERS_TYPED = EventFields.Int("number_of_letters_typed")
    private val LANGUAGE = EventFields.Language

    private val USAGE_NAVIGATE = GROUP.registerVarargEvent("item.chosen", USAGE_VIEW, SELECTED_USAGE,
                                                           NUMBER_OF_USAGES,
                                                           NUMBER_OF_LETTERS_TYPED, LANGUAGE)

    @JvmStatic
    fun logItemChosen(project: Project,
                      usageView: UsageView?,
                      numberOfSelectedUsage: Int,
                      numberUsages: Int,
                      numberOfLettersTyped: Int,
                      language: Language) {
      USAGE_NAVIGATE.log(project, USAGE_VIEW.with(usageView), SELECTED_USAGE.with(numberOfSelectedUsage),
                         NUMBER_OF_USAGES.with(numberUsages),
                         NUMBER_OF_LETTERS_TYPED.with(numberOfLettersTyped), LANGUAGE.with(language))
    }

  }
}