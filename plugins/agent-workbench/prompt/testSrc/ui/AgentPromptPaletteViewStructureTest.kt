// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.ActionLink
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
  fun trailingComponentIsPreservedAfterRenderAndAppearsAfterChips() {
    runInEdtAndWait {
      val chips = AgentPromptContextChipsComponent {}
      val trailing = ActionLink(AgentPromptBundle.message("popup.context.add")) {}
      chips.trailingComponent = trailing
      com.intellij.util.ui.DialogUtil.registerMnemonic(trailing)

      // Render with entries — trailing must survive removeAll + re-add
      val entries = listOf(
        ContextEntry(item = AgentPromptContextItem(rendererId = "test", title = "FileA.kt", body = "a")),
        ContextEntry(item = AgentPromptContextItem(rendererId = "test", title = "FileB.kt", body = "b")),
            )
            chips.render(entries)

            val children = chips.component.components.toList()
            // Two chips + trailing link
            assertThat(children.size).isGreaterThanOrEqualTo(3)
            assertThat(children.last()).isSameAs(trailing)

            // Re-render with fewer entries — trailing still last
            val fewerEntries = listOf(
                ContextEntry(item = AgentPromptContextItem(rendererId = "test", title = "FileA.kt", body = "a")),
            )
            chips.render(fewerEntries)

      val childrenAfter = chips.component.components.toList()
      assertThat(childrenAfter.size).isGreaterThanOrEqualTo(2)
      assertThat(childrenAfter.last()).isSameAs(trailing)
      assertThat(trailing.text).isEqualTo("Add Context")
      assertThat(trailing.mnemonic).isEqualTo(KeyEvent.VK_C)
      assertThat(trailing.displayedMnemonicIndex).isEqualTo(4)
    }
  }

    private fun xInRoot(component: java.awt.Component, root: JPanel): Int {
        return SwingUtilities.convertPoint(component.parent, component.location, root).x
    }
}
