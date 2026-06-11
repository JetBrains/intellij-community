// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBanner
import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBannerState
import com.intellij.openapi.components.service

class AgentSessionCostHintBanner(
  private val hintStateService: AgentSessionCostHintStateService = service(),
) : AgentWorkbenchHintBanner(
  titleText = AgentSessionsBundle.message("toolwindow.session.cost.hint.title"),
  bodyText = AgentSessionsBundle.message("toolwindow.session.cost.hint.body"),
  enableText = AgentSessionsBundle.message("toolwindow.session.cost.hint.enable"),
  dismissText = AgentSessionsBundle.message("toolwindow.session.cost.hint.dismiss"),
) {
  init {
    collectEligibility(hintStateService.eligibleFlow)
    collectAcknowledged(hintStateService.acknowledgedFlow)
    collectFeatureEnabled(AgentSessionCostPresentationSettings.enabledFlow)
    launchPeriodicStateSync {
      AgentSessionCostPresentationSettings.syncEnabledState()
    }
  }

  override fun enableFeature() {
    AgentSessionCostPresentationSettings.setEnabled(true)
  }

  override fun acknowledgeHint() {
    hintStateService.acknowledge()
  }

  override fun shouldAcknowledge(state: AgentWorkbenchHintBannerState): Boolean {
    return shouldAcknowledgeAgentSessionCostHint(
      eligible = state.eligible,
      acknowledged = state.acknowledged,
      settingEnabled = state.featureEnabled,
    )
  }

  override fun shouldShow(state: AgentWorkbenchHintBannerState): Boolean {
    return shouldShowAgentSessionCostHint(
      eligible = state.eligible,
      acknowledged = state.acknowledged,
      settingEnabled = state.featureEnabled,
    )
  }
}
