// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionCandidate
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import javax.swing.SwingUtilities

@TestApplication
class AgentPromptPaletteViewLayoutTest {
  @Test
  fun composerContextClusterIsHiddenWhenNoContextChipsArePresentAndAddContextControlIsUnavailable() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel()
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChipsPanel,
        addContextVisible = false,
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isFalse()
    }
  }

  @Test
  fun composerContextClusterIsShownWhenAddContextControlIsTheOnlyVisibleControl() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val view = createPaletteView(promptArea = promptArea)

      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isTrue()
      assertThat(view.addContextButton.isVisible).isTrue()
    }
  }

  @Test
  fun composerContextClusterIsShownWhenContextChipsExist() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChipsPanel = JPanel().apply {
        add(JPanel())
      }
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChipsPanel,
        addContextVisible = false,
      )

      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isTrue()
      assertThat(contextChipsPanel.isVisible).isTrue()
      assertThat(contextChipsPanel.height).isGreaterThan(0)
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
      val view = createPaletteView(
        promptArea = promptArea,
        contextChipsPanel = contextChipsPanel,
        addContextVisible = false,
      )
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      layoutPopupRoot(view.rootPanel)
      assertThat(view.composerContextPanel.isVisible).isTrue()
      val promptHeightWithContext = foundPromptArea.height
      assertThat(promptHeightWithContext).isGreaterThan(0)

      contextChipsPanel.removeAll()
      layoutPopupRoot(view.rootPanel)

      assertThat(view.composerContextPanel.isVisible).isFalse()
      assertThat(foundPromptArea.height).isGreaterThan(promptHeightWithContext)
    }
  }

  @Test
  fun addContextControlKeepsStablePositionWhenContextChipsAreAdded() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val contextChips = AgentPromptContextChipsComponent {}
      contextChips.render(listOf(createContextEntry(title = "File", body = "src/Main.java")))
      val view = createPaletteView(promptArea = promptArea, contextChipsPanel = contextChips.component)

      layoutPopupRoot(view.rootPanel)
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))
      val initialLocation = locationInRoot(view.addContextButton, view.rootPanel)
      val firstChipLocation = locationInRoot(contextChips.component.components.first(), view.rootPanel)
      val promptAreaLocation = locationInRoot(foundPromptArea, view.rootPanel)

      assertThat(initialLocation.x).isEqualTo(promptAreaLocation.x)
      assertThat(firstChipLocation.x).isEqualTo(promptAreaLocation.x)

      contextChips.render(
        listOf(
          createContextEntry(title = "File", body = "src/Main.java"),
          createContextEntry(title = "Symbol", body = "main"),
          createContextEntry(title = "Caret Context", body = "6-12"),
        )
      )
      layoutPopupRoot(view.rootPanel)

      assertThat(locationInRoot(view.addContextButton, view.rootPanel)).isEqualTo(initialLocation)
      assertThat(locationInRoot(contextChips.component, view.rootPanel).y).isLessThan(initialLocation.y)
    }
  }

  @Test
  fun enteringPromptTextKeepsSuggestionRowVisibleWithoutChangingPromptHeight() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val suggestions = AgentPromptSuggestionsComponent {}
      suggestions.render(
        listOf(
          AgentPromptSuggestionCandidate(
            id = "tests.fix",
            label = "Fix failing tests",
            promptText = "Fix the failing tests.",
          )
        )
      )
      val view = createPaletteView(promptArea = promptArea, suggestionsPanel = suggestions.component)
      val foundPromptArea = checkNotNull(findPromptArea(view.rootPanel, promptArea))

      layoutPopupRoot(view.rootPanel)
      val promptHeightWithSuggestions = foundPromptArea.height

      assertThat(view.suggestionsPanel.isVisible).isTrue()
      assertThat(view.suggestionsPanel.height).isGreaterThan(0)
      assertThat(promptHeightWithSuggestions).isGreaterThan(0)

      promptArea.text = "Draft prompt"
      layoutPopupRoot(view.rootPanel)

      assertThat(view.suggestionsPanel.isVisible).isTrue()
      assertThat(view.suggestionsPanel.height).isGreaterThan(0)
      assertThat(foundPromptArea.height).isEqualTo(promptHeightWithSuggestions)
    }
  }

  @Test
  fun suggestionStripStaysAbovePromptAreaAndAwayFromComposerContext() {
    runInEdtAndWait {
      val promptArea = EditorTextField()
      val suggestions = AgentPromptSuggestionsComponent {}
      suggestions.render(
        listOf(
          AgentPromptSuggestionCandidate(
            id = "tests.fix",
            label = "Fix failing tests",
            promptText = "Fix the failing tests.",
          )
        )
      )
      val contextChips = AgentPromptContextChipsComponent {}
      contextChips.render(listOf(createContextEntry(title = "File", body = "src/Main.java")))
      val view = createPaletteView(
        promptArea = promptArea,
        suggestionsPanel = suggestions.component,
        contextChipsPanel = contextChips.component,
      )

      layoutPopupRoot(view.rootPanel)

      val promptAreaLocation = locationInRoot(checkNotNull(findPromptArea(view.rootPanel, promptArea)), view.rootPanel)
      val suggestionsLocation = locationInRoot(view.suggestionsPanel, view.rootPanel)
      val composerContextLocation = locationInRoot(view.composerContextPanel, view.rootPanel)

      assertThat(suggestionsLocation.y + view.suggestionsPanel.height).isLessThanOrEqualTo(promptAreaLocation.y)
      assertThat(promptAreaLocation.y + promptArea.height).isLessThanOrEqualTo(composerContextLocation.y)
    }
  }

  private fun createContextEntry(title: String, body: String): ContextEntry {
    return ContextEntry(
      item = AgentPromptContextItem(
        rendererId = "test",
        title = title,
        body = body,
      )
    )
  }

  private fun createPaletteView(
    promptArea: EditorTextField,
    suggestionsPanel: JPanel = JPanel(),
    contextChipsPanel: JPanel = JPanel(),
    addContextVisible: Boolean = true,
  ): AgentPromptPaletteView {
    return createAgentPromptPaletteView(
      promptArea = promptArea,
      suggestionsPanel = suggestionsPanel,
      contextChipsPanel = contextChipsPanel,
      onProviderIconClicked = {},
      onExistingTaskSelected = {},
    ).apply {
      addContextButton.isVisible = addContextVisible
    }
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

  private fun locationInRoot(component: java.awt.Component, root: JPanel): java.awt.Point {
    return SwingUtilities.convertPoint(component.parent, component.location, root)
  }
}
