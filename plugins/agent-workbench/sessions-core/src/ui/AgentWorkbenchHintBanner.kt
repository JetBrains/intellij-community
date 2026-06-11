// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.ui

import com.intellij.openapi.application.UI
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import org.jetbrains.annotations.Nls
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AgentWorkbenchHintBannerState(
  val eligible: Boolean = false,
  val acknowledged: Boolean = false,
  val featureEnabled: Boolean = false,
)

abstract class AgentWorkbenchHintBanner protected constructor(
  titleText: @Nls String,
  bodyText: @Nls String,
  enableText: @NlsContexts.Button String,
  dismissText: @NlsContexts.Button String,
) : JPanel(BorderLayout()) {
  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

  private val periodicSyncs = mutableListOf<PeriodicSync>()
  private val periodicSyncJobs = mutableListOf<Job>()
  private var state: AgentWorkbenchHintBannerState = AgentWorkbenchHintBannerState()

  init {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBColor.border(), 1),
      JBUI.Borders.empty(8),
    )
    isVisible = false

    add(createTextPanel(titleText = titleText, bodyText = bodyText), BorderLayout.CENTER)
    add(createActionsPanel(enableText = enableText, dismissText = dismissText), BorderLayout.SOUTH)
  }

  protected fun collectEligibility(flow: Flow<Boolean>) {
    collect(flow) { eligible ->
      updateState { current -> current.copy(eligible = eligible) }
    }
  }

  protected fun collectAcknowledged(flow: Flow<Boolean>) {
    collect(flow) { acknowledged ->
      updateState { current -> current.copy(acknowledged = acknowledged) }
    }
  }

  protected fun collectFeatureEnabled(flow: Flow<Boolean>) {
    collect(flow) { featureEnabled ->
      updateState { current -> current.copy(featureEnabled = featureEnabled) }
    }
  }

  protected fun updateEligibility(eligible: Boolean) {
    updateState { current -> current.copy(eligible = eligible) }
  }

  protected fun updateFeatureEnabledState(featureEnabled: Boolean) {
    updateState { current -> current.copy(featureEnabled = featureEnabled) }
  }

  protected fun launchPeriodicStateSync(interval: Duration = 1.seconds, action: () -> Unit) {
    periodicSyncs += PeriodicSync(interval = interval, action = action)
    if (isDisplayable) {
      startPeriodicSyncJobsIfNeeded()
    }
  }

  override fun addNotify() {
    super.addNotify()
    startPeriodicSyncJobsIfNeeded()
  }

  override fun removeNotify() {
    cancelPeriodicSyncJobs()
    scope.cancel("${javaClass.simpleName} removed")
    super.removeNotify()
  }

  protected abstract fun enableFeature()

  protected abstract fun acknowledgeHint()

  protected abstract fun shouldAcknowledge(state: AgentWorkbenchHintBannerState): Boolean

  protected abstract fun shouldShow(state: AgentWorkbenchHintBannerState): Boolean

  private fun collect(flow: Flow<Boolean>, update: (Boolean) -> Unit) {
    scope.launch {
      flow.collect(update)
    }
  }

  private fun updateState(update: (AgentWorkbenchHintBannerState) -> AgentWorkbenchHintBannerState) {
    state = update(state)
    syncVisibility()
  }

  private fun syncVisibility() {
    if (shouldAcknowledge(state)) {
      acknowledgeHint()
    }

    val shouldShow = shouldShow(state)
    if (isVisible != shouldShow) {
      isVisible = shouldShow
      refreshLayoutWithParent()
    }
  }

  private fun startPeriodicSyncJobsIfNeeded() {
    if (periodicSyncJobs.isNotEmpty()) return

    periodicSyncJobs += periodicSyncs.map { sync ->
      scope.launch(Dispatchers.Default) {
        while (isActive) {
          sync.action()
          delay(sync.interval)
        }
      }
    }
  }

  private fun cancelPeriodicSyncJobs() {
    periodicSyncJobs.forEach(Job::cancel)
    periodicSyncJobs.clear()
  }

  private fun createTextPanel(titleText: @Nls String, bodyText: @Nls String): JPanel {
    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(JLabel(titleText).apply {
        font = font.deriveFont(font.style or Font.BOLD)
      })
      add(Box.createVerticalStrut(JBUI.scale(4)))
      add(JLabel(bodyText))
    }
  }

  private fun createActionsPanel(
    enableText: @NlsContexts.Button String,
    dismissText: @NlsContexts.Button String,
  ): JPanel {
    return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      add(JButton(enableText).apply {
        addActionListener {
          enableFeature()
          acknowledgeHint()
        }
      })
      add(JButton(dismissText).apply {
        addActionListener { acknowledgeHint() }
      })
    }
  }

  private data class PeriodicSync(
    val interval: Duration,
    val action: () -> Unit,
  )
}
