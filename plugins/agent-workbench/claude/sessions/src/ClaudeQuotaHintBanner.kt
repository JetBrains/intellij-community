// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBanner
import com.intellij.agent.workbench.sessions.core.ui.AgentWorkbenchHintBannerState
import com.intellij.openapi.components.service

internal class ClaudeQuotaHintBanner(
  private val hintStateService: ClaudeQuotaHintStateService = service(),
) : AgentWorkbenchHintBanner(
  titleText = ClaudeSessionsBundle.message("toolwindow.claude.quota.hint.title"),
  bodyText = ClaudeSessionsBundle.message("toolwindow.claude.quota.hint.body"),
  enableText = ClaudeSessionsBundle.message("toolwindow.claude.quota.hint.enable"),
  dismissText = ClaudeSessionsBundle.message("toolwindow.claude.quota.hint.dismiss"),
) {
  init {
    collectEligibility(hintStateService.eligibleFlow)
    collectAcknowledged(hintStateService.acknowledgedFlow)
    collectFeatureEnabled(ClaudeQuotaStatusBarWidgetSettings.enabledFlow)
    launchPeriodicStateSync {
      ClaudeQuotaStatusBarWidgetSettings.syncEnabledState()
    }
  }

  override fun enableFeature() {
    ClaudeQuotaStatusBarWidgetSettings.setEnabled(true)
  }

  override fun acknowledgeHint() {
    hintStateService.acknowledge()
  }

  override fun shouldAcknowledge(state: AgentWorkbenchHintBannerState): Boolean {
    return shouldAcknowledgeClaudeQuotaHint(
      eligible = state.eligible,
      acknowledged = state.acknowledged,
      widgetEnabled = state.featureEnabled,
    )
  }

  override fun shouldShow(state: AgentWorkbenchHintBannerState): Boolean {
    return shouldShowClaudeQuotaHint(
      eligible = state.eligible,
      acknowledged = state.acknowledged,
      widgetEnabled = state.featureEnabled,
    )
  }
}
