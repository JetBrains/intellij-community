// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.claude

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ClickListener
import com.intellij.ui.JBColor
import com.intellij.util.LazyInitializer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer

private const val WIDGET_ID = "claude.quota"
private const val UI_REFRESH_INTERVAL_MS = 5_000
private const val UI_REFRESH_INITIAL_DELAY_MS = 1_000
private const val WARNING_QUOTA_PERCENT = 80

internal class ClaudeQuotaStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = WIDGET_ID

  override fun getDisplayName(): String = AgentSessionsBundle.message("status.bar.claude.quota.display.name")

  override fun isEnabledByDefault(): Boolean = true

  override fun createWidget(project: Project): StatusBarWidget = ClaudeQuotaStatusBarWidget()
}

internal class ClaudeQuotaStatusBarWidget : CustomStatusBarWidget, Activatable {
  private val myComponent = LazyInitializer.create { ClaudeQuotaPanel() }
  private var myTimer: Timer? = null

  override fun showNotify() {
    val component = myComponent.get()
    service<ClaudeQuotaService>().startPolling()
    component.updateState()
    val timer = Timer(UI_REFRESH_INTERVAL_MS) { component.updateState() }
    timer.initialDelay = UI_REFRESH_INITIAL_DELAY_MS
    timer.start()
    myTimer = timer
  }

  override fun hideNotify() {
    myTimer?.stop()
    myTimer = null
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation? = null

  override fun ID(): String = WIDGET_ID

  override fun getComponent(): JComponent = myComponent.get()

  private inner class ClaudeQuotaPanel : JPanel(BorderLayout(JBUI.scale(4), 0)) {
    private val sessionBarColor = JBColor.namedColor("ClaudeQuota.sessionBarBackground", JBColor(0xE8874B, 0xD4783E))
    private val weeklyBarColor = JBColor.namedColor("ClaudeQuota.weeklyBarBackground", JBColor(0x4A8FE2, 0x5B9BD5))
    private val warningBarColor = JBColor.namedColor("ClaudeQuota.warningBarBackground", JBColor(0xE8874B, 0xD4783E))

    private val sessionBar = createBar(sessionBarColor, stripeWidth = 4)
    private val weeklyBar = createBar(weeklyBarColor, stripeWidth = 4)
    private val iconLabel = JLabel(IconLoader.getIcon("/icons/claude@14x14.svg", ClaudeQuotaStatusBarWidget::class.java))
    private val barsBox = JPanel()
    private var shouldDisplay = false

    init {
      isOpaque = false
      isFocusable = false
      border = JBUI.Borders.empty(0, 4)
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

    override fun getPreferredSize(): Dimension {
      val sup = super.getPreferredSize()
      if (!shouldDisplay) {
        return Dimension(0, sup.height)
      }
      return Dimension(sup.width.coerceAtLeast(JBUI.scale(80)), sup.height)
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

      if (shouldDisplay != newShouldDisplay) {
        shouldDisplay = newShouldDisplay
        iconLabel.isVisible = newShouldDisplay
        barsBox.isVisible = newShouldDisplay
        revalidate()
      }

      if (!newShouldDisplay) {
        toolTipText = null
        repaint()
        return
      }

      if (hasError) {
        toolTipText = AgentSessionsBundle.message("quota.error")
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
        val clamped = session.coerceIn(0, 100)
        sessionBar.value = clamped
        sessionBar.foreground = if (isWarningQuota(clamped)) warningBarColor else sessionBarColor
      }
      if (weekly != null) {
        val clamped = weekly.coerceIn(0, 100)
        weeklyBar.value = clamped
        weeklyBar.foreground = if (isWarningQuota(clamped)) warningBarColor else weeklyBarColor
      }

      val now = System.currentTimeMillis()
      toolTipText = formatWidgetTooltip(info, now)
      repaint()
    }
  }
}

internal fun isWarningQuota(percent: Int): Boolean = percent > WARNING_QUOTA_PERCENT

private fun createBar(color: JBColor, stripeWidth: Int = 4): JProgressBar {
  return JProgressBar(0, 100).apply {
    value = 0
    isOpaque = false
    isStringPainted = false
    foreground = color
    putClientProperty("ProgressBar.stripeWidth", stripeWidth)
  }
}

internal fun dominantPercent(info: ClaudeQuotaInfo): Int? {
  val session = info.fiveHourPercent
  val weekly = info.sevenDayPercent
  return when {
    session != null && weekly != null -> maxOf(session, weekly)
    session != null -> session
    weekly != null -> weekly
    else -> null
  }
}

internal fun formatWidgetText(info: ClaudeQuotaInfo): String {
  val session = info.fiveHourPercent
  val weekly = info.sevenDayPercent
  return when {
    session != null && weekly != null -> {
      if (session >= weekly) {
        AgentSessionsBundle.message("status.bar.claude.quota.text", session, "5h")
      }
      else {
        AgentSessionsBundle.message("status.bar.claude.quota.text", weekly, "7d")
      }
    }
    session != null -> AgentSessionsBundle.message("status.bar.claude.quota.text", session, "5h")
    weekly != null -> AgentSessionsBundle.message("status.bar.claude.quota.text", weekly, "7d")
    else -> ""
  }
}

@Suppress("HardCodedStringLiteral")
internal fun formatWidgetTooltip(info: ClaudeQuotaInfo, now: Long): @Nls String {
  val parts = mutableListOf<String>()
  if (info.fiveHourPercent != null) {
    val resetText = if (info.fiveHourReset != null) formatQuotaResetTime(info.fiveHourReset, now) else ""
    parts.add(AgentSessionsBundle.message("status.bar.claude.quota.tooltip.session", info.fiveHourPercent, resetText))
  }
  if (info.sevenDayPercent != null) {
    val resetText = if (info.sevenDayReset != null) formatQuotaResetTime(info.sevenDayReset, now) else ""
    parts.add(AgentSessionsBundle.message("status.bar.claude.quota.tooltip.weekly", info.sevenDayPercent, resetText))
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
