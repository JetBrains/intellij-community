// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.ui

import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaStatusBarWidgetSettings
import com.intellij.agent.workbench.sessions.state.AgentSessionsTreeUiStateService
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal class ClaudeQuotaHintController(
  private val treeUiStateService: AgentSessionsTreeUiStateService,
  private val quotaHintPanel: ClaudeQuotaHintPanel,
) {
  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)

  private var claudeQuotaHintEligible: Boolean = false
  private var claudeQuotaHintAcknowledged: Boolean = false
  private var isClaudeQuotaWidgetEnabled: Boolean = false

  fun start() {
    scope.launch {
      treeUiStateService.claudeQuotaHintEligibleFlow.collect { eligible ->
        claudeQuotaHintEligible = eligible
        syncClaudeQuotaHintState()
      }
    }

    scope.launch {
      treeUiStateService.claudeQuotaHintAcknowledgedFlow.collect { acknowledged ->
        claudeQuotaHintAcknowledged = acknowledged
        syncClaudeQuotaHintState()
      }
    }

    scope.launch {
      ClaudeQuotaStatusBarWidgetSettings.enabledFlow.collect { enabled ->
        isClaudeQuotaWidgetEnabled = enabled
        syncClaudeQuotaHintState()
      }
    }

    scope.launch(Default) {
      while (isActive) {
        ClaudeQuotaStatusBarWidgetSettings.syncEnabledState()
        delay(1.seconds)
      }
    }
  }

  fun dispose() {
    scope.cancel("Claude quota hint controller disposed")
  }

  private fun syncClaudeQuotaHintState() {
    if (shouldAcknowledgeClaudeQuotaHint(
        eligible = claudeQuotaHintEligible,
        acknowledged = claudeQuotaHintAcknowledged,
        widgetEnabled = isClaudeQuotaWidgetEnabled,
      )
    ) {
      treeUiStateService.acknowledgeClaudeQuotaHint()
    }
    quotaHintPanel.isVisible = shouldShowClaudeQuotaHint(
      eligible = claudeQuotaHintEligible,
      acknowledged = claudeQuotaHintAcknowledged,
      widgetEnabled = isClaudeQuotaWidgetEnabled,
    )
  }
}
