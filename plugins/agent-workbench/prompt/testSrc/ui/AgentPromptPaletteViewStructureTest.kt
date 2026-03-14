// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@TestApplication
class AgentPromptPaletteViewStructureTest {
  @Test
  fun promptAreaIsRenderedExactlyOnceAndSharedAcrossTabs() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val rootPanel = view.rootPanel
      val promptAreasBeforeSwitch = collectComponentsOfType(rootPanel, EditorTextField::class.java)
      assertThat(promptAreasBeforeSwitch).containsExactly(promptArea)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(rootPanel)

      val promptAreasAfterSwitch = collectComponentsOfType(rootPanel, EditorTextField::class.java)
      assertThat(promptAreasAfterSwitch).containsExactly(promptArea)
    }
  }

  @Test
  fun promptAreaInstanceRemainsStableWhenTargetModeChanges() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val rootPanel = view.rootPanel
      layoutPopupRoot(rootPanel)
      val initialPromptArea = checkNotNull(findPromptArea(rootPanel, promptArea))

      view.tabbedPane.selectedIndex = 0
      view.existingTaskScrollPane.isVisible = false
      layoutPopupRoot(rootPanel)
      assertThat(findPromptArea(rootPanel, promptArea)).isSameAs(initialPromptArea)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(rootPanel)
      assertThat(findPromptArea(rootPanel, promptArea)).isSameAs(initialPromptArea)
    }
  }

  @Test
  fun providerOptionsPanelIsRenderedOnceWhenProvided() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeCheckBox = JBCheckBox("Plan mode").apply { isFocusable = false }
      val providerOptionsPanel = JPanel().apply { add(planModeCheckBox) }
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        providerOptionsPanel = providerOptionsPanel,
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val planModeCheckBoxes = collectComponentsOfType(view.rootPanel, JBCheckBox::class.java)
      assertThat(planModeCheckBoxes).containsExactly(planModeCheckBox)
      assertThat(view.providerOptionsPanel).isSameAs(providerOptionsPanel)
      assertThat(planModeCheckBox.isFocusable).isFalse()
    }
  }

  @Test
  fun providerOptionsPanelIsRightAlignedNextToProviderIcon() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeCheckBox = JBCheckBox("Plan mode").apply { isFocusable = false }
      val providerOptionsPanel = JPanel().apply { add(planModeCheckBox) }
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        providerOptionsPanel = providerOptionsPanel,
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

  @Test
  fun addContextControlUsesTextLabelAndInlineMnemonic() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      assertThat(view.addContextButton.text).isEqualTo("Add Context")
      assertThat(view.addContextButton.mnemonic).isEqualTo(KeyEvent.VK_C)
      assertThat(view.addContextButton.displayedMnemonicIndex).isEqualTo(4)
    }
  }

  @Test
  fun composerContextControlsArePlacedInsidePromptPanel() {
    runInEdtAndWait {
      val contextChipsPanel = JPanel().apply {
        add(JPanel())
      }
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = contextChipsPanel,
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.addContextButton, view.bottomPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(contextChipsPanel, view.bottomPanel)).isFalse()
    }
  }

  private fun xInRoot(component: java.awt.Component, root: JPanel): Int {
    return SwingUtilities.convertPoint(component.parent, component.location, root).x
  }
}
