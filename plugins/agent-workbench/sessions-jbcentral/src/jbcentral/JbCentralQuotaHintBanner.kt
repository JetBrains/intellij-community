// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

class JbCentralQuotaHintBanner(
  private val hintStateService: JbCentralQuotaHintStateService = service(),
) : JPanel(BorderLayout()) {
  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

  private var eligible: Boolean = false
  private var acknowledged: Boolean = false
  private var widgetEnabled: Boolean = false

  init {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBColor.border(), 1),
      JBUI.Borders.empty(8),
    )
    isVisible = false

    val textPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(JLabel(AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.title")).apply {
        font = font.deriveFont(font.style or Font.BOLD)
      })
      add(Box.createVerticalStrut(JBUI.scale(4)))
      add(JLabel(AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.body")))
    }

    val actionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      add(JButton(AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.enable")).apply {
        addActionListener {
          JbCentralQuotaStatusBarWidgetSettings.setEnabled(true)
          hintStateService.acknowledge()
        }
      })
      add(JButton(AgentSessionsBundle.message("toolwindow.jbcentral.quota.hint.dismiss")).apply {
        addActionListener { hintStateService.acknowledge() }
      })
    }

    add(textPanel, BorderLayout.CENTER)
    add(actionsPanel, BorderLayout.SOUTH)

    scope.launch {
      hintStateService.eligibleFlow.collect { currentEligible ->
        eligible = currentEligible
        syncVisibility()
      }
    }
    scope.launch {
      hintStateService.acknowledgedFlow.collect { currentAcknowledged ->
        acknowledged = currentAcknowledged
        syncVisibility()
      }
    }
    scope.launch {
      JbCentralQuotaStatusBarWidgetSettings.enabledFlow.collect { enabled ->
        widgetEnabled = enabled
        syncVisibility()
      }
    }
    scope.launch(Dispatchers.Default) {
      while (isActive) {
        hintStateService.setEligible(JbCentralQuotaCliSupport.isAvailable())
        JbCentralQuotaStatusBarWidgetSettings.syncEnabledState()
        delay(1.seconds)
      }
    }
  }

  override fun removeNotify() {
    scope.cancel("JBCentral quota hint banner removed")
    super.removeNotify()
  }

  private fun syncVisibility() {
    if (shouldAcknowledgeJbCentralQuotaHint(eligible = eligible, acknowledged = acknowledged, widgetEnabled = widgetEnabled)) {
      hintStateService.acknowledge()
    }
    isVisible = shouldShowJbCentralQuotaHint(eligible = eligible, acknowledged = acknowledged, widgetEnabled = widgetEnabled)
  }
}
