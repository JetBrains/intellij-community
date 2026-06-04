// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.ProgressBarUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar

// Money-green for the remaining amount, matching the status-bar widget.
private val MONEY_TEXT_COLOR: Color = JBColor(0x55A76A, 0x57965C)

// Pale neutral gray for the progress bar, matching the status-bar widget.
private val PALE_BAR_COLOR: Color = JBColor(0x6C707E, 0x868A91)

private val RUNS_OUT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private const val SPARKLINE_DAYS = 14

internal fun showJbCentralQuotaPopup(anchor: JComponent, info: JbCentralQuotaInfo) {
  var popup: JBPopup? = null
  val content = buildContent(info) { popup?.cancel() }
  popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null)
    .setCancelOnClickOutside(true)
    .setCancelOnWindowDeactivation(true)
    .setRequestFocus(false)
    .createPopup()
  popup.show(RelativePoint(anchor, Point(0, -popup.content.preferredSize.height)))
}

private fun buildContent(info: JbCentralQuotaInfo, closePopup: () -> Unit): JComponent {
  val panel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = JBUI.Borders.empty(8, 12)
  }
  val gap = JBUI.scale(6)

  val identity = buildIdentity(info)
  if (identity.isNotBlank()) {
    panel.add(JBLabel(identity).apply { font = font.deriveFont(Font.BOLD) }.alignLeft())
    panel.add(verticalGap(gap))
  }

  panel.add(remainingLine(info))
  panel.add(verticalGap(JBUI.scale(2)))
  panel.add(remainingBar(info))

  forecastLines(info).takeIf { it.isNotEmpty() }?.let { lines ->
    panel.add(verticalGap(gap))
    for (line in lines) {
      panel.add(JBLabel(line).alignLeft())
    }
  }

  panel.add(verticalGap(gap))
  panel.add(sparklineSection())

  panel.add(verticalGap(gap))
  panel.add(ActionLink(AgentSessionsBundle.message("status.bar.jbcentral.quota.popup.refresh")) {
    service<JbCentralQuotaService>().requestRefresh()
    closePopup()
  }.alignLeft())

  return panel
}

private fun remainingLine(info: JbCentralQuotaInfo): JComponent {
  return JBLabel(
    AgentSessionsBundle.message("status.bar.jbcentral.quota.popup.remaining", "$${info.remainingUsd}", "$${info.totalUsd}"))
    .apply { foreground = MONEY_TEXT_COLOR }
    .alignLeft()
}

private fun remainingBar(info: JbCentralQuotaInfo): JComponent {
  return JProgressBar(0, 100).apply {
    value = remainingPercent(info)
    isStringPainted = false
    isOpaque = false
    putClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY, PALE_BAR_COLOR)
    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
  }.alignLeft()
}

private fun forecastLines(info: JbCentralQuotaInfo): List<String> {
  val reset = parseResetDate(info.resetDateText) ?: return emptyList()
  val used = info.usedUsd.toDoubleOrNull() ?: return emptyList()
  val total = info.totalUsd.toDoubleOrNull() ?: return emptyList()
  val forecast = forecastSpend(used, total, reset) ?: return emptyList()
  val rateLine = AgentSessionsBundle.message(
    "status.bar.jbcentral.quota.popup.daily.rate", "%.2f".format(Locale.US, forecast.dailyRateUsd))
  val outcomeLine = when (val outcome = forecast.outcome) {
    is JbCentralQuotaForecast.Outcome.Surplus ->
      AgentSessionsBundle.message("status.bar.jbcentral.quota.popup.surplus", "%.2f".format(Locale.US, outcome.remainingUsd))
    is JbCentralQuotaForecast.Outcome.RunsOut ->
      AgentSessionsBundle.message("status.bar.jbcentral.quota.popup.runs.out", RUNS_OUT_DATE_FORMATTER.format(outcome.date))
  }
  return listOf(rateLine, outcomeLine)
}

private fun sparklineSection(): JComponent {
  val section = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
  }
  val points = dailySpend(service<JbCentralQuotaUsageHistoryService>().samples(), SPARKLINE_DAYS)
  if (points.all { it.spentUsd == 0.0 }) {
    section.add(JBLabel(AgentSessionsBundle.message("status.bar.jbcentral.quota.popup.collecting")).apply {
      foreground = PALE_BAR_COLOR
    }.alignLeft())
    return section
  }
  section.add(JBLabel(AgentSessionsBundle.message("status.bar.jbcentral.quota.popup.spend.title", SPARKLINE_DAYS)).apply {
    foreground = PALE_BAR_COLOR
  }.alignLeft())
  section.add(verticalGap(JBUI.scale(2)))
  section.add(JbCentralQuotaSparkline(points).alignLeft())
  return section
}

private fun verticalGap(height: Int): JComponent = JBUI.Panels.simplePanel().apply {
  isOpaque = false
  preferredSize = Dimension(0, height)
  maximumSize = Dimension(Int.MAX_VALUE, height)
}

private fun <T : JComponent> T.alignLeft(): T = apply { alignmentX = Component.LEFT_ALIGNMENT }

private fun buildIdentity(info: JbCentralQuotaInfo): @NlsSafe String =
  listOfNotNull(info.email, info.licenseName)
    .filter { it.isNotBlank() }
    .joinToString(separator = " · ")
