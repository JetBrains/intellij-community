// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
class AgentPromptPaletteViewLayoutTest {
  @Test
  fun contextRowIsHiddenWhenNoContextChipsArePresent() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel()
      val view = createPaletteView(promptArea = promptArea, contextChipsPanel = contextChipsPanel)

      layoutPopupRoot(view.rootPanel)

      val contextRow = checkNotNull(contextChipsPanel.parent as? JPanel)
      assertThat(contextRow.isVisible).isFalse()
    }
  }

  @Test
  fun contextRowIsShownWhenContextChipsExist() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel().apply {
        add(JPanel())
      }
      val view = createPaletteView(promptArea = promptArea, contextChipsPanel = contextChipsPanel)

      layoutPopupRoot(view.rootPanel)

      val contextRow = checkNotNull(contextChipsPanel.parent as? JPanel)
      assertThat(contextRow.isVisible).isTrue()
      assertThat(contextRow.height).isGreaterThan(0)
    }
  }

  @Test
  fun firstSwitchToExistingKeepsPromptAreaVisible() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea)
      populateExistingTasks(view = view, count = 200)

      view.tabbedPane.selectedIndex = 0
      view.existingTaskScrollPane.isVisible = false
      layoutPopupRoot(view.rootPanel)

      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))
      assertThat(foundPromptArea.height).isGreaterThan(0)

      view.tabbedPane.selectedIndex = 1
      view.existingTaskScrollPane.isVisible = true
      layoutPopupRoot(view.rootPanel)

      assertThat(foundPromptArea.height).isGreaterThan(0)
    }
  }

  @Test
  fun repeatedModeSwitchesKeepPromptVisible() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea)
      populateExistingTasks(view = view, count = 150)
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      repeat(8) { iteration ->
        val existingMode = iteration % 2 == 1
        view.tabbedPane.selectedIndex = if (existingMode) 1 else 0
        view.existingTaskScrollPane.isVisible = existingMode
        layoutPopupRoot(view.rootPanel)

        assertThat(foundPromptArea.height).isGreaterThan(0)
      }
    }
  }

  @Test
  fun existingTaskPaneIsBoundedToPreventPromptStarvation() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea)

      assertThat(view.existingTaskList.visibleRowCount).isEqualTo(4)
      assertThat(view.existingTaskScrollPane.preferredSize.height).isGreaterThan(0)
      assertThat(view.existingTaskScrollPane.minimumSize.height).isGreaterThan(0)
      assertThat(view.existingTaskScrollPane.preferredSize.height)
        .isLessThan(view.rootPanel.preferredSize.height)
      assertThat(view.existingTaskScrollPane.minimumSize.height)
        .isLessThan(view.rootPanel.minimumSize.height)
    }
  }

  @Test
  fun removingLastContextChipCollapsesContextRowWithoutHidingPrompt() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel().apply {
        add(JPanel())
      }
      val view = createPaletteView(promptArea = promptArea, contextChipsPanel = contextChipsPanel)
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))
      val contextRow = checkNotNull(contextChipsPanel.parent as? JPanel)

      layoutPopupRoot(view.rootPanel)
      assertThat(contextRow.isVisible).isTrue()
      val promptHeightWithContext = foundPromptArea.height
      assertThat(promptHeightWithContext).isGreaterThan(0)

      contextChipsPanel.removeAll()
      layoutPopupRoot(view.rootPanel)

      assertThat(contextRow.isVisible).isFalse()
      assertThat(foundPromptArea.height).isGreaterThan(promptHeightWithContext)
    }
  }

  private fun createPaletteView(
    promptArea: EditorTextField,
    contextChipsPanel: JPanel = JPanel(),
  ): AgentPromptPaletteView {
    return createAgentPromptPaletteView(
      promptArea = promptArea,
      contextChipsPanel = contextChipsPanel,
      onProviderIconClicked = {},
      onExistingTaskSelected = {},
    )
  }

  private fun populateExistingTasks(view: AgentPromptPaletteView, count: Int) {
    repeat(count) { index ->
      val id = "thread-${index + 1}"
      view.existingTaskListModel.addElement(
        ThreadEntry(
          id = id,
          displayText = "Thread $id",
          secondaryText = "just now",
        )
      )
    }
  }
}
