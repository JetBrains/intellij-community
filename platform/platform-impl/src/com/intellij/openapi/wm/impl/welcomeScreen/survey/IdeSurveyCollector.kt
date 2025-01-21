// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.survey

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object IdeSurveyCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ide.survey", 1)

  val trialSurveyAnswer = EventFields.Enum<TrialSurveyOptions>("answer")
  val answerIndex = EventFields.Int("index")

  private val trialSurveyAnsweredEvent = GROUP.registerEvent("trial.survey.answered", trialSurveyAnswer, answerIndex)

  fun logTrialSurveyAnswered(answer: TrialSurveyOptions, index: Int) = trialSurveyAnsweredEvent.log(answer, index)

  override fun getGroup(): EventLogGroup = GROUP
}