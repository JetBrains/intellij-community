// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ui

import com.intellij.openapi.progress.util.ProgressBarUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.JBUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchQuotaStatusBarUiTest {
  @Test
  fun progressBarUsesSharedStatusBarStyle() {
    runInEdtAndWait {
      val bar = createAgentWorkbenchQuotaStatusBarProgressBar()

      assertThat(bar.minimum).isEqualTo(0)
      assertThat(bar.maximum).isEqualTo(100)
      assertThat(bar.value).isEqualTo(0)
      assertThat(bar.isOpaque).isFalse()
      assertThat(bar.isStringPainted).isFalse()
      assertThat(bar.getClientProperty("ProgressBar.stripeWidth")).isEqualTo(4)
    }
  }

  @Test
  fun progressBarColorHelpersPreserveForegroundAndPaintModes() {
    runInEdtAndWait {
      val foregroundBar = createAgentWorkbenchQuotaStatusBarProgressBar()
      val progressPaintBar = createAgentWorkbenchQuotaStatusBarProgressBar()
      val foregroundColor = Color(0x12, 0x34, 0x56)
      val progressPaintColor = Color(0x65, 0x43, 0x21)

      foregroundBar.setQuotaStatusBarForeground(foregroundColor)
      progressPaintBar.setQuotaStatusBarProgressPaint(progressPaintColor)

      assertThat(foregroundBar.foreground).isEqualTo(foregroundColor)
      assertThat(foregroundBar.getClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY)).isNull()
      assertThat(progressPaintBar.getClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY)).isEqualTo(progressPaintColor)
    }
  }

  @Test
  fun panelCollapsesWhenHiddenAndKeepsVisibleMinimumWidth() {
    runInEdtAndWait {
      val panel = TestQuotaStatusBarPanel(AgentWorkbenchQuotaStatusBarLayout(horizontalGap = 4, visibleMinimumWidth = 80))

      assertThat(panel.preferredSize.width).isEqualTo(0)
      assertThat(panel.child.isVisible).isFalse()

      panel.updateVisible(true)

      assertThat(panel.child.isVisible).isTrue()
      assertThat(panel.preferredSize.width).isEqualTo(JBUI.scale(80))

      panel.updateVisible(false)

      assertThat(panel.child.isVisible).isFalse()
      assertThat(panel.preferredSize.width).isEqualTo(0)
    }
  }

  @Test
  fun sharedLayoutsMatchQuotaWidgetWidths() {
    assertThat(AgentWorkbenchQuotaStatusBarUi.claudeLayout.horizontalGap).isEqualTo(4)
    assertThat(AgentWorkbenchQuotaStatusBarUi.claudeLayout.visibleMinimumWidth).isEqualTo(80)
    assertThat(AgentWorkbenchQuotaStatusBarUi.jbCentralLayout.horizontalGap).isEqualTo(6)
    assertThat(AgentWorkbenchQuotaStatusBarUi.jbCentralLayout.visibleMinimumWidth).isEqualTo(96)
  }
}

private class TestQuotaStatusBarPanel(
  layout: AgentWorkbenchQuotaStatusBarLayout,
) : AgentWorkbenchQuotaStatusBarPanel(layout) {
  val child = FixedPreferredSizePanel().apply {
    isVisible = false
  }

  init {
    add(child, BorderLayout.CENTER)
  }

  fun updateVisible(visible: Boolean) {
    setQuotaStatusBarVisible(visible, child)
  }
}

private class FixedPreferredSizePanel : JPanel() {
  override fun getPreferredSize(): Dimension = Dimension(16, 8)
}
