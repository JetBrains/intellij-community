// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.EmptyIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.SwingConstants
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
      val planModeCheckBox = createAgentPromptHeaderCheckBox("Plan mode")
      val providerOptionsPanel = JPanel(HorizontalLayout(8, SwingConstants.CENTER)).apply { add(planModeCheckBox) }
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        providerOptionsPanel = providerOptionsPanel,
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val planModeCheckBoxes = collectComponentsOfType(view.rootPanel, JBCheckBox::class.java)
      assertThat(planModeCheckBoxes).contains(planModeCheckBox, view.containerModeCheckBox)
      assertThat(planModeCheckBoxes.count { it === planModeCheckBox }).isEqualTo(1)
      assertThat(view.providerOptionsPanel).isSameAs(providerOptionsPanel)
      assertThat(SwingUtilities.isDescendingFrom(providerOptionsPanel, view.rightHeaderPanel)).isTrue()
      assertThat(planModeCheckBox.isFocusable).isFalse()
    }
  }

  @Test
  fun rightHeaderPanelIsRightAlignedAfterTargetTabs() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val planModeCheckBox = createAgentPromptHeaderCheckBox("Plan mode")
      val providerOptionsPanel = JPanel(HorizontalLayout(8, SwingConstants.CENTER)).apply { add(planModeCheckBox) }
      val view = createAgentPromptPaletteView(
        promptArea = promptArea,
        contextChipsPanel = JPanel(),
        providerOptionsPanel = providerOptionsPanel,
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )
      view.containerModeCheckBox.isVisible = true
      view.providerIconLabel.icon = EmptyIcon.ICON_16

      layoutPopupRoot(view.rootPanel)

      val tabbedPaneX = xInRoot(view.tabbedPane, view.rootPanel)
      val rightHeaderX = xInRoot(view.rightHeaderPanel, view.rootPanel)
      val containerModeX = xInRoot(view.containerModeCheckBox, view.rootPanel)
      val planModeX = xInRoot(planModeCheckBox, view.rootPanel)
      val promptLibraryX = xInRoot(view.promptLibraryIconLabel, view.rootPanel)
      val providerIconX = xInRoot(view.providerIconLabel, view.rootPanel)
      assertThat(view.rightHeaderPanel.layout).isInstanceOf(HorizontalLayout::class.java)
      assertThat(rightHeaderX).isGreaterThan(tabbedPaneX)
      assertThat(containerModeX).isGreaterThan(view.rootPanel.width / 2)
      assertThat(planModeX).isGreaterThan(containerModeX)
      assertThat(promptLibraryX).isGreaterThan(planModeX)
      assertThat(providerIconX).isGreaterThan(promptLibraryX)
      assertThat(providerIconX + view.providerIconLabel.width).isGreaterThan(view.rootPanel.width - 32)
    }
  }

  @Test
  fun containerModeControlIsAHeaderLaunchOption() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      assertThat(SwingUtilities.isDescendingFrom(view.containerModeCheckBox, view.rightHeaderPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(view.containerModeCheckBox, view.bottomPanel)).isFalse()
      assertThat(view.containerModeCheckBox.text).isEqualTo("Run in container")
      assertThat(view.containerModeCheckBox.mnemonic).isEqualTo(KeyEvent.VK_R)
      assertThat(view.containerModeCheckBox.displayedMnemonicIndex).isEqualTo(0)
      assertThat(view.containerModeCheckBox.isFocusable).isFalse()
      assertThat(view.containerModeCheckBox.isOpaque).isFalse()
      assertThat(view.containerModeCheckBox.font).isEqualTo(JBCheckBox().font)
    }
  }

  @Test
  fun headerOptionCheckBoxesUseSearchEverywhereCheckboxStyle() {
    runInEdtAndWait {
      val planModeCheckBox = createAgentPromptHeaderCheckBox("Plan mode")
      val providerOptionsPanel = JPanel(HorizontalLayout(8, SwingConstants.CENTER)).apply { add(planModeCheckBox) }
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        providerOptionsPanel = providerOptionsPanel,
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      val defaultCheckBox = JBCheckBox()
      assertThat(planModeCheckBox.font).isEqualTo(defaultCheckBox.font)
      assertThat(view.containerModeCheckBox.font).isEqualTo(defaultCheckBox.font)
      assertThat(planModeCheckBox.isOpaque).isEqualTo(view.containerModeCheckBox.isOpaque)
      assertThat(planModeCheckBox.isFocusable).isEqualTo(view.containerModeCheckBox.isFocusable)
      assertThat(planModeCheckBox.border.getBorderInsets(planModeCheckBox))
        .isEqualTo(view.containerModeCheckBox.border.getBorderInsets(view.containerModeCheckBox))
    }
  }

  @Test
  fun promptLibraryControlIsSingleHeaderEntryBeforeProviderIcon() {
    runInEdtAndWait {
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        contextChipsPanel = JPanel(),
        onPromptLibraryClicked = {},
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(SwingUtilities.isDescendingFrom(view.promptLibraryIconLabel, view.rootPanel)).isTrue()
      assertThat(view.promptLibraryIconLabel.toolTipText).contains("Open prompt library")
      assertThat(xInRoot(view.promptLibraryIconLabel, view.rootPanel)).isLessThan(xInRoot(view.providerIconLabel, view.rootPanel))
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

  @Test
  fun suggestionsPanelIsPlacedInsidePromptPanel() {
    runInEdtAndWait {
      val suggestionsPanel = JPanel()
      val view = createAgentPromptPaletteView(
        promptArea = EditorTextField(),
        suggestionsPanel = suggestionsPanel,
        contextChipsPanel = JPanel(),
        onProviderIconClicked = {},
        onExistingTaskSelected = {},
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(view.suggestionsPanel).isSameAs(suggestionsPanel)
      assertThat(SwingUtilities.isDescendingFrom(suggestionsPanel, view.promptPanel)).isTrue()
      assertThat(SwingUtilities.isDescendingFrom(suggestionsPanel, view.bottomPanel)).isFalse()
      assertThat(SwingUtilities.isDescendingFrom(suggestionsPanel, view.composerContextPanel)).isFalse()
    }
  }

  private fun xInRoot(component: java.awt.Component, root: JPanel): Int {
    return SwingUtilities.convertPoint(component.parent, component.location, root).x
  }
}
