// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.ui.AgentWorkbenchQuotaStatusBarPanel
import com.intellij.agent.workbench.ui.AgentWorkbenchQuotaStatusBarRefreshLoop
import com.intellij.agent.workbench.ui.AgentWorkbenchQuotaStatusBarUi
import com.intellij.agent.workbench.ui.createAgentWorkbenchQuotaStatusBarProgressBar
import com.intellij.agent.workbench.ui.setQuotaStatusBarProgressPaint
import com.intellij.ide.setToolTipText
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ClickListener
import com.intellij.util.LazyInitializer
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import kotlin.math.roundToInt
import javax.swing.JComponent
import javax.swing.JLabel

internal const val JBCENTRAL_QUOTA_WIDGET_ID = "jbcentral.quota"
private const val LOW_REMAINING_PERCENT = 20
private const val QUOTA_REFRESH_INTERVAL_MS = 60_000

internal class JbCentralQuotaStatusBarWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = JBCENTRAL_QUOTA_WIDGET_ID

  override fun getDisplayName(): String = AgentSessionsBundle.message("status.bar.jbcentral.quota.display.name")

  override fun isEnabledByDefault(): Boolean = false

  override fun isAvailable(project: Project): Boolean =
    AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)

  override fun createWidget(project: Project): StatusBarWidget = JbCentralQuotaStatusBarWidget()
}

internal class JbCentralQuotaStatusBarWidget : CustomStatusBarWidget, Activatable {
  private val myComponent = LazyInitializer.create { JbCentralQuotaPanel() }
  private val myRefreshLoop = AgentWorkbenchQuotaStatusBarRefreshLoop(QUOTA_REFRESH_INTERVAL_MS) {
    service<JbCentralQuotaService>().requestRefresh()
  }

  override fun showNotify() {
    myComponent.get().requestInitialRefresh()
    myRefreshLoop.start()
  }

  override fun hideNotify() {
    myRefreshLoop.stop()
  }

  override fun getPresentation(): StatusBarWidget.WidgetPresentation? = null

  override fun ID(): String = JBCENTRAL_QUOTA_WIDGET_ID

  override fun getComponent(): JComponent = myComponent.get()

  private inner class JbCentralQuotaPanel : AgentWorkbenchQuotaStatusBarPanel(AgentWorkbenchQuotaStatusBarUi.jbCentralLayout) {
    @Suppress("RAW_SCOPE_CREATION")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

    private val label = JLabel()
    private val bar = createAgentWorkbenchQuotaStatusBarProgressBar()
    private var initialRefreshRequested = false

    init {
      label.foreground = AgentWorkbenchQuotaStatusBarUi.jbCentralMoneyTextColor
      label.isVisible = false
      bar.isVisible = false
      add(label, BorderLayout.WEST)
      add(bar, BorderLayout.CENTER)

      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          val info = service<JbCentralQuotaService>().state.value.quotaInfo
          if (info != null) {
            showJbCentralQuotaPopup(this@JbCentralQuotaPanel, info)
          }
          else {
            service<JbCentralQuotaService>().requestRefresh()
          }
          return true
        }
      }.installOn(this, true)
      UiNotifyConnector.installOn(this, this@JbCentralQuotaStatusBarWidget)

      scope.launch {
        service<JbCentralQuotaService>().state.collect { state ->
          updateState(state)
        }
      }
    }

    override fun removeNotify() {
      scope.cancel("JBCentral quota widget removed")
      super.removeNotify()
    }

    fun requestInitialRefresh() {
      if (initialRefreshRequested) return
      initialRefreshRequested = true
      service<JbCentralQuotaService>().requestRefresh()
    }

    private fun updateState(state: JbCentralQuotaState) {
      val info = state.quotaInfo
      val newShouldDisplay = info != null
      setQuotaStatusBarVisible(newShouldDisplay, label, bar)

      if (info == null) {
        toolTipText = null
        repaint()
        return
      }

      val remaining = remainingPercent(info)
      label.text = formatJbCentralQuotaText(info)
      bar.value = remaining
      val barColor = if (isLowRemainingQuota(remaining)) {
        AgentWorkbenchQuotaStatusBarUi.warningBarColor
      }
      else {
        AgentWorkbenchQuotaStatusBarUi.jbCentralNormalBarColor
      }
      bar.setQuotaStatusBarProgressPaint(barColor)
      setToolTipText(HtmlChunk.raw(formatJbCentralQuotaTooltip(info)))
      repaint()
    }
  }
}

internal fun formatJbCentralQuotaText(info: JbCentralQuotaInfo): @NlsSafe String {
  return "$${info.remainingUsd}"
}

internal fun formatJbCentralQuotaTooltip(info: JbCentralQuotaInfo): @NlsSafe String {
  val parts = buildList {
    val identity = listOfNotNull(info.email, info.licenseName)
      .filter { it.isNotBlank() }
      .joinToString(separator = " · ")
    if (identity.isNotBlank()) {
      add(StringUtil.escapeXmlEntities(identity))
    }
    add(StringUtil.escapeXmlEntities("Remaining: $${info.remainingUsd} of $${info.totalUsd}"))
    info.percentUsed?.let { usedPercent ->
      add(StringUtil.escapeXmlEntities("Used: ${"%.1f".format(java.util.Locale.US, usedPercent)}% ($${info.usedUsd})"))
    }
    info.resetDateText?.let { add(StringUtil.escapeXmlEntities("Resets: $it")) }
  }
  return "<html>${parts.joinToString("<br>")}</html>"
}

internal fun remainingPercent(info: JbCentralQuotaInfo): Int {
  val percentUsed = info.percentUsed ?: calculatePercentFromAmounts(info.usedUsd, info.totalUsd) ?: 0.0
  return (100.0 - percentUsed).coerceIn(0.0, 100.0).roundToInt()
}

internal fun isLowRemainingQuota(remainingPercent: Int): Boolean = remainingPercent < LOW_REMAINING_PERCENT

private fun calculatePercentFromAmounts(usedUsd: String, totalUsd: String): Double? {
  val used = usedUsd.toDoubleOrNull() ?: return null
  val total = totalUsd.toDoubleOrNull() ?: return null
  if (total <= 0.0) return null
  return used / total * 100.0
}
