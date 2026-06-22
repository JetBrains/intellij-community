// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

import com.intellij.platform.ai.agent.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.ui.AgentWorkbenchQuotaStatusBarPanel
import com.intellij.agent.workbench.ui.AgentWorkbenchQuotaStatusBarRefreshLoop
import com.intellij.agent.workbench.ui.AgentWorkbenchQuotaStatusBarUi
import com.intellij.agent.workbench.ui.createAgentWorkbenchQuotaStatusBarProgressBar
import com.intellij.agent.workbench.ui.setQuotaStatusBarForeground
import com.intellij.ide.setToolTipText
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ClickListener
import com.intellij.util.LazyInitializer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar

internal const val CLAUDE_QUOTA_WIDGET_ID = "claude.quota"
private const val UI_REFRESH_INTERVAL_MS = 5_000
private const val UI_REFRESH_INITIAL_DELAY_MS = 1_000
private const val WARNING_QUOTA_PERCENT = 80

internal class ClaudeQuotaStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = CLAUDE_QUOTA_WIDGET_ID

  override fun getDisplayName(): String = ClaudeSessionsBundle.message("status.bar.claude.quota.display.name")

  override fun isEnabledByDefault(): Boolean = false

  override fun createWidget(project: Project): StatusBarWidget = ClaudeQuotaStatusBarWidget()
}

internal class ClaudeQuotaStatusBarWidget : CustomStatusBarWidget, Activatable {
  private val myComponent = LazyInitializer.create { ClaudeQuotaPanel() }
  private val myRefreshLoop = AgentWorkbenchQuotaStatusBarRefreshLoop(
    intervalMs = UI_REFRESH_INTERVAL_MS,
    initialDelayMs = UI_REFRESH_INITIAL_DELAY_MS,
  ) { myComponent.get().updateState() }

  override fun showNotify() {
    val component = myComponent.get()
    service<ClaudeQuotaService>().startPolling()
    component.updateState()
    myRefreshLoop.start()
  }

  override fun hideNotify() {
    myRefreshLoop.stop()
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation? = null

  override fun ID(): String = CLAUDE_QUOTA_WIDGET_ID

  override fun getComponent(): JComponent = myComponent.get()

  private inner class ClaudeQuotaPanel : AgentWorkbenchQuotaStatusBarPanel(AgentWorkbenchQuotaStatusBarUi.claudeLayout) {
    private val sessionBar = createAgentWorkbenchQuotaStatusBarProgressBar().apply {
      setQuotaStatusBarForeground(AgentWorkbenchQuotaStatusBarUi.claudeSessionBarColor)
    }
    private val weeklyBar = createAgentWorkbenchQuotaStatusBarProgressBar().apply {
      setQuotaStatusBarForeground(AgentWorkbenchQuotaStatusBarUi.claudeWeeklyBarColor)
    }
    private val iconLabel = JLabel(AgentWorkbenchCommonIcons.ClaudeGray)
    private val barsBox = JPanel()

    init {
      iconLabel.isVisible = false

      add(iconLabel, BorderLayout.WEST)

      barsBox.apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        isVisible = false
        add(Box.createVerticalGlue())
        add(sessionBar)
        add(Box.createVerticalStrut(JBUI.scale(4)))
        add(weeklyBar)
        add(Box.createVerticalGlue())
      }
      add(barsBox, BorderLayout.CENTER)

      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          service<ClaudeQuotaService>().requestRefresh()
          updateState()
          return true
        }
      }.installOn(this, true)
      UiNotifyConnector.installOn(this, this@ClaudeQuotaStatusBarWidget)
    }

    fun updateState() {
      if (!isDisplayable) return

      val state = service<ClaudeQuotaService>().state.value
      val info = state.quotaInfo
      val hasError = state.error != null && state.error != ClaudeQuotaError.NO_CREDENTIALS
      val noData = info == null && !hasError
      val noCredentials = state.error == ClaudeQuotaError.NO_CREDENTIALS
      val allNull = info != null && info.fiveHourPercent == null && info.sevenDayPercent == null
      val newShouldDisplay = !(noData || noCredentials || allNull)

      setQuotaStatusBarVisible(newShouldDisplay, iconLabel, barsBox)

      if (!newShouldDisplay) {
        toolTipText = null
        repaint()
        return
      }

      if (hasError) {
        setToolTipText(HtmlChunk.text(ClaudeSessionsBundle.message("quota.error")))
        sessionBar.isVisible = false
        weeklyBar.isVisible = false
        repaint()
        return
      }

      val session = info!!.fiveHourPercent
      val weekly = info.sevenDayPercent

      sessionBar.isVisible = session != null
      weeklyBar.isVisible = weekly != null
      if (session != null) {
        updateBar(sessionBar, session, AgentWorkbenchQuotaStatusBarUi.claudeSessionBarColor)
      }
      if (weekly != null) {
        updateBar(weeklyBar, weekly, AgentWorkbenchQuotaStatusBarUi.claudeWeeklyBarColor)
      }

      val now = System.currentTimeMillis()
      setToolTipText(HtmlChunk.raw(formatWidgetTooltip(info, now)))
      repaint()
    }

    private fun updateBar(bar: JProgressBar, percent: Int, defaultColor: Color) {
      val clamped = percent.coerceIn(0, 100)
      bar.value = clamped
      val color = if (isWarningQuota(clamped)) {
        AgentWorkbenchQuotaStatusBarUi.warningBarColor
      }
      else {
        defaultColor
      }
      bar.setQuotaStatusBarForeground(color)
    }
  }
}

internal fun isWarningQuota(percent: Int): Boolean = percent > WARNING_QUOTA_PERCENT

@Suppress("HardCodedStringLiteral")
internal fun formatWidgetTooltip(info: ClaudeQuotaInfo, now: Long): @Nls String {
  val parts = mutableListOf<String>()
  if (info.fiveHourPercent != null) {
    val resetText = if (info.fiveHourReset != null) formatQuotaResetTime(info.fiveHourReset, now) else ""
    parts.add(ClaudeSessionsBundle.message("status.bar.claude.quota.tooltip.session", info.fiveHourPercent, resetText))
  }
  if (info.sevenDayPercent != null) {
    val resetText = if (info.sevenDayReset != null) formatQuotaResetTime(info.sevenDayReset, now) else ""
    parts.add(ClaudeSessionsBundle.message("status.bar.claude.quota.tooltip.weekly", info.sevenDayPercent, resetText))
  }
  return if (parts.size > 1) "<html>${parts.joinToString("<br>")}</html>" else parts.firstOrNull() ?: ""
}

internal fun formatQuotaResetTime(timestamp: Long, now: Long): String {
  val totalSeconds = ((timestamp - now) / 1000L).coerceAtLeast(0)
  val totalMinutes = totalSeconds / 60
  val days = totalMinutes / (60 * 24)
  val hours = (totalMinutes % (60 * 24)) / 60
  val minutes = totalMinutes % 60
  return when {
    days > 0 -> "${days}d ${hours}h"
    hours > 0 -> "${hours}h ${minutes}m"
    else -> "${minutes}m"
  }
}
