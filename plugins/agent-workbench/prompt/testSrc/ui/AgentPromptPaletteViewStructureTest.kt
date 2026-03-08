// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Component
import javax.swing.JPanel
import javax.swing.SwingUtilities

@TestApplication
class AgentPromptPaletteViewStructureTest {
  @Test
  fun promptAreaIsRenderedExactlyOnceAndSharedAcrossTabs() {
    runInEdtAndWait {
      val promptArea = JBTextArea(6, 100)
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val rootPanel = view.rootPanel
      val promptAreasBeforeSwitch = collectComponentsOfType(rootPanel, JBTextArea::class.java)
      assertThat(promptAreasBeforeSwitch).containsExactly(promptArea)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(rootPanel)

      val promptAreasAfterSwitch = collectComponentsOfType(rootPanel, JBTextArea::class.java)
      assertThat(promptAreasAfterSwitch).containsExactly(promptArea)
    }
  }

  @Test
  fun promptScrollPaneInstanceRemainsStableWhenTargetModeChanges() {
    runInEdtAndWait {
      val promptArea = JBTextArea(6, 100)
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val rootPanel = view.rootPanel
      layoutPopupRoot(rootPanel)
      val initialPromptScrollPane = checkNotNull(findPromptScrollPane(rootPanel, promptArea))

      view.tabbedPane.selectedIndex = 0
      view.existingTaskScrollPane.isVisible = false
      layoutPopupRoot(rootPanel)
      assertThat(findPromptScrollPane(rootPanel, promptArea)).isSameAs(initialPromptScrollPane)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(rootPanel)
      assertThat(findPromptScrollPane(rootPanel, promptArea)).isSameAs(initialPromptScrollPane)
    }
  }

  @Test
  fun planModeCheckBoxIsRenderedOnceWhenProvidedAndNotFocusable() {
    runInEdtAndWait {
      val promptArea = JBTextArea(6, 100)
      val planModeCheckBox = JBCheckBox("Plan mode").apply {
        isFocusable = false
      }
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        planModeCheckBox = planModeCheckBox,
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val planModeCheckBoxes = collectComponentsOfType(view.rootPanel, JBCheckBox::class.java)
      assertThat(planModeCheckBoxes).containsExactly(planModeCheckBox)
      assertThat(view.planModeCheckBox).isSameAs(planModeCheckBox)
      assertThat(planModeCheckBox.isFocusable).isFalse()
    }
  }

  @Test
  fun planModeCheckBoxIsRightAlignedNextToProviderIcon() {
    runInEdtAndWait {
      val promptArea = JBTextArea(6, 100)
      val planModeCheckBox = JBCheckBox("Plan mode").apply {
        isFocusable = false
      }
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        planModeCheckBox = planModeCheckBox,
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      layoutPopupRoot(view.rootPanel)

      val checkBoxX = xInRoot(planModeCheckBox, view.rootPanel)
      val providerIconX = xInRoot(view.providerIconLabel, view.rootPanel)
      assertThat(checkBoxX).isGreaterThan(view.rootPanel.width / 2)
      assertThat(providerIconX).isGreaterThan(checkBoxX)
    }
  }

  private fun xInRoot(component: Component, root: JPanel): Int {
    return SwingUtilities.convertPoint(component.parent, component.location, root).x
  }
}
