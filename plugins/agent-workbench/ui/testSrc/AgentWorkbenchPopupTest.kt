// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchPopupTest {
  @Test
  fun rendererClearsSelectionBackgroundWhenCheckedRowLosesListSelection() {
    runInEdtAndWait {
      val checkedRow = AgentWorkbenchPopupRow(
        text = "Codex",
        primaryIcon = TestIcon,
        secondaryIcon = TestIcon,
        selected = true,
      )
      val nextRow = AgentWorkbenchPopupRow(
        text = "Pi",
        primaryIcon = TestIcon,
      )
      val renderer = AgentWorkbenchPopupRowRenderer()
      val list = JList(arrayOf(checkedRow, nextRow))

      val selectedComponent = renderer.getListCellRendererComponent(list, checkedRow, 0, true, true)

      assertThat(selectedComponent).isInstanceOf(SelectablePanel::class.java)
      val selectedPanel = selectedComponent as SelectablePanel
      assertThat(selectedPanel.isOpaque).isTrue()
      assertThat(selectedPanel.background).isEqualTo(JBUI.CurrentTheme.Popup.BACKGROUND)
      assertThat(selectedPanel.selectionColor).isNotNull()

      val unselectedComponent = renderer.getListCellRendererComponent(list, checkedRow, 0, false, false)

      assertThat(unselectedComponent).isSameAs(selectedPanel)
      assertThat(selectedPanel.isOpaque).isTrue()
      assertThat(selectedPanel.background).isEqualTo(JBUI.CurrentTheme.Popup.BACKGROUND)
      assertThat(selectedPanel.selectionColor).isNull()
      assertThat(renderPixel(selectedPanel, x = 20).rgb).isEqualTo(JBUI.CurrentTheme.Popup.BACKGROUND.rgb)
    }
  }

  @Test
  fun rendererPaintsSelectionAcrossThePopupRow() {
    runInEdtAndWait {
      val checkedRow = AgentWorkbenchPopupRow(
        text = "Codex",
        primaryIcon = TestIcon,
        secondaryIcon = TestIcon,
        selected = true,
      )
      val renderer = AgentWorkbenchPopupRowRenderer()
      val list = JList(arrayOf(checkedRow))

      val selectedPanel = renderer.getListCellRendererComponent(list, checkedRow, 0, true, true) as SelectablePanel

      assertThat(selectedPanel.selectionColor).isEqualTo(UIUtil.getListSelectionBackground(true))
      assertThat(renderPixel(selectedPanel, x = 20).rgb).isEqualTo(UIUtil.getListSelectionBackground(true).rgb)
      assertThat(renderPixel(selectedPanel, x = 220).rgb).isEqualTo(UIUtil.getListSelectionBackground(true).rgb)
    }
  }

  @Test
  fun stepBuildsNestedRowsFromProviderWhenSubstepOpens() {
    runInEdtAndWait {
      var providerCalls = 0
      var nestedRowText = "Loading models..."
      val modelRow = AgentWorkbenchPopupRow(
        text = "Default",
        subRowsProvider = {
          providerCalls++
          listOf(AgentWorkbenchPopupRow(text = nestedRowText))
        },
      )
      val step = AgentWorkbenchPopupStep(listOf(modelRow))

      assertThat(step.hasSubstep(modelRow)).isTrue()
      assertThat(providerCalls).isEqualTo(1)

      nestedRowText = "GPT-5.1 Codex"
      val nestedStep = step.onChosen(modelRow, finalChoice = false)

      assertThat(nestedStep).isInstanceOf(AgentWorkbenchPopupStep::class.java)
      assertThat(providerCalls).isEqualTo(2)
      assertThat((nestedStep as AgentWorkbenchPopupStep).values.map { row -> row.text }).containsExactly("GPT-5.1 Codex")
    }
  }
}

private fun renderPixel(component: JComponent, x: Int, width: Int = 240): Color {
  val height = component.preferredSize.height
  component.setBounds(0, 0, width, height)
  layoutRecursively(component)
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
  val graphics = image.createGraphics()
  try {
    graphics.color = PopupBackgroundMarker
    graphics.fillRect(0, 0, width, height)
    component.paint(graphics)
  }
  finally {
    graphics.dispose()
  }
  return Color(image.getRGB(x, height / 2))
}

private fun layoutRecursively(component: Component) {
  component.doLayout()
  if (component is java.awt.Container) {
    component.components.forEach(::layoutRecursively)
  }
}

private val PopupBackgroundMarker = Color(0x12, 0x34, 0x56)

private object TestIcon : Icon {
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = Unit

  override fun getIconWidth(): Int = 16

  override fun getIconHeight(): Int = 16
}
