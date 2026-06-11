// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBanner
import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBannerState
import com.intellij.openapi.components.service

class JbCentralQuotaHintBanner(
  private val hintStateService: JbCentralQuotaHintStateService = service(),
) : AgentWorkbenchHintBanner(
  titleText = AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.title"),
  bodyText = AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.body"),
  enableText = AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.enable"),
  dismissText = AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.dismiss"),
) {
  init {
    collectEligibility(hintStateService.eligibleFlow)
    collectAcknowledged(hintStateService.acknowledgedFlow)
    collectFeatureEnabled(JbCentralQuotaStatusBarWidgetSettings.enabledFlow)
    launchPeriodicStateSync {
      hintStateService.setEligible(JbCentralQuotaCliSupport.isAvailable())
      JbCentralQuotaStatusBarWidgetSettings.syncEnabledState()
    }
  }

  override fun enableFeature() {
    JbCentralQuotaStatusBarWidgetSettings.setEnabled(true)
  }

  override fun acknowledgeHint() {
    hintStateService.acknowledge()
  }

  override fun shouldAcknowledge(state: AgentWorkbenchHintBannerState): Boolean {
    return shouldAcknowledgeJbCentralQuotaHint(
      eligible = state.eligible,
      acknowledged = state.acknowledged,
      widgetEnabled = state.featureEnabled,
    )
  }

  override fun shouldShow(state: AgentWorkbenchHintBannerState): Boolean {
    return shouldShowJbCentralQuotaHint(
      eligible = state.eligible,
      acknowledged = state.acknowledged,
      widgetEnabled = state.featureEnabled,
    )
  }
}
