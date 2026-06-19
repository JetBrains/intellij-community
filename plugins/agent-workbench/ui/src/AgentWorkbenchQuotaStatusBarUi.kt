// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ui

import com.intellij.openapi.progress.util.ProgressBarUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer

@ApiStatus.Internal
object AgentWorkbenchQuotaStatusBarUi {
  val warningBarColor: Color = JBColor(0xE8874B, 0xD4783E)
  val claudeSessionBarColor: Color = warningBarColor
  val claudeWeeklyBarColor: Color = JBColor(0x4A8FE2, 0x5B9BD5)
  val jbCentralMoneyTextColor: Color = JBColor(0x55A76A, 0x57965C)
  val jbCentralNormalBarColor: Color = JBColor(0x6C707E, 0x868A91)

  val claudeLayout: AgentWorkbenchQuotaStatusBarLayout = AgentWorkbenchQuotaStatusBarLayout(horizontalGap = 4, visibleMinimumWidth = 80)
  val jbCentralLayout: AgentWorkbenchQuotaStatusBarLayout = AgentWorkbenchQuotaStatusBarLayout(horizontalGap = 6, visibleMinimumWidth = 96)
}

@ApiStatus.Internal
data class AgentWorkbenchQuotaStatusBarLayout(
  val horizontalGap: Int,
  val visibleMinimumWidth: Int,
)

@ApiStatus.Internal
open class AgentWorkbenchQuotaStatusBarPanel(
  private val layout: AgentWorkbenchQuotaStatusBarLayout,
) : JPanel(BorderLayout(JBUI.scale(layout.horizontalGap), 0)) {
  private var shouldDisplay = false

  init {
    isOpaque = false
    isFocusable = false
    border = JBUI.Borders.empty(0, 4)
  }

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    if (!shouldDisplay) {
      return Dimension(0, preferredSize.height)
    }
    return Dimension(preferredSize.width.coerceAtLeast(JBUI.scale(layout.visibleMinimumWidth)), preferredSize.height)
  }

  protected fun setQuotaStatusBarVisible(visible: Boolean, vararg components: Component) {
    if (shouldDisplay == visible) return

    shouldDisplay = visible
    components.forEach { component -> component.isVisible = visible }
    revalidate()
  }
}

@ApiStatus.Internal
class AgentWorkbenchQuotaStatusBarRefreshLoop(
  private val intervalMs: Int,
  private val initialDelayMs: Int = intervalMs,
  private val refresh: () -> Unit,
) {
  private var timer: Timer? = null

  fun start() {
    stop()
    timer = Timer(intervalMs) { refresh() }.apply {
      initialDelay = initialDelayMs
      start()
    }
  }

  fun stop() {
    timer?.stop()
    timer = null
  }
}

@ApiStatus.Internal
fun createAgentWorkbenchQuotaStatusBarProgressBar(): JProgressBar {
  return JProgressBar(0, 100).apply {
    value = 0
    isOpaque = false
    isStringPainted = false
    putClientProperty("ProgressBar.stripeWidth", 4)
  }
}

@ApiStatus.Internal
fun JProgressBar.setQuotaStatusBarForeground(color: Color) {
  foreground = color
}

@ApiStatus.Internal
fun JProgressBar.setQuotaStatusBarProgressPaint(color: Color) {
  putClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY, color)
}
