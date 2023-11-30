// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.onboarding

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object OnboardingTipsStatistics : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  private val GROUP = EventLogGroup("onboarding.tips.statistics", 4)

  private val projectsWithTipsField = EventFields.Int("projects_with_tips")
  private val firstTimeActionUsedField = EventFields.Boolean("first_time_used")
  private val promotedActionField = EventFields.String("action_id", promotedActions)

  private val onboardingTipsInstalledEvent = GROUP.registerEvent("onboarding.tips.installed", projectsWithTipsField)
  private val disableOnboardingTipsEvent = GROUP.registerEvent("tips.disabled", projectsWithTipsField)
  private val hideOnboardingTipsDisableProposalEvent = GROUP.registerEvent("hide.disable.proposal", projectsWithTipsField)
  private val promotedActionUsedEvent = GROUP.registerEvent("promoted.action.used", promotedActionField, projectsWithTipsField,
                                                            firstTimeActionUsedField)

  @JvmStatic
  fun logOnboardingTipsInstalled(project: Project?, projectsWithTips: Int) = onboardingTipsInstalledEvent.log(project, projectsWithTips)
  @JvmStatic
  fun logDisableOnboardingTips(project: Project?, projectsWithTips: Int) = disableOnboardingTipsEvent.log(project, projectsWithTips)
  @JvmStatic
  fun logHideOnboardingTipsDisableProposal(project: Project?, projectsWithTips: Int) = hideOnboardingTipsDisableProposalEvent.log(project,
                                                                                                                                  projectsWithTips)

  @JvmStatic
  fun logPromotedActionUsedEvent(project: Project?,
                                 actionId: String,
                                 projectsWithTips: Int,
                                 firstTimeUsed: Boolean) = promotedActionUsedEvent.log(project, actionId, projectsWithTips, firstTimeUsed)
}