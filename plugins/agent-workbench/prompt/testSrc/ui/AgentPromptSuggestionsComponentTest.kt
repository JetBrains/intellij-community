// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionCandidate
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.ActionLink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptSuggestionsComponentTest {
  @Test
  fun renderHidesSuggestionStripWhenEmpty() {
    runInEdtAndWait {
      val component = AgentPromptSuggestionsComponent {}

      component.render(emptyList())

      assertThat(component.component.isVisible).isFalse()
      assertThat(collectComponentsOfType(component.component, ActionLink::class.java)).isEmpty()
    }
  }

  @Test
  fun renderShowsAtMostThreeInlineActionsAndDispatchesSelection() {
    runInEdtAndWait {
      var selected: AgentPromptSuggestionCandidate? = null
      val component = AgentPromptSuggestionsComponent { candidate ->
        selected = candidate
      }

      val first = candidate(id = "tests.fix", label = "Fix failing tests", promptText = "Fix the failing tests.")
      val second = candidate(id = "tests.explain", label = "Explain failures", promptText = "Explain the failing tests.")
      val third = candidate(id = "tests.review", label = "Review tests", promptText = "Review the test setup.")
      val fourth = candidate(id = "tests.extend", label = "More tests", promptText = "Extend test coverage.")
      component.render(listOf(first, second, third, fourth))

      val actions = collectComponentsOfType(component.component, ActionLink::class.java)
      assertThat(component.component.isVisible).isTrue()
      assertThat(component.component.name).isEqualTo("promptSuggestionsStrip")
      assertThat(actions.map(ActionLink::getText)).containsExactly(first.label, second.label, third.label)
      assertThat(actions.map(ActionLink::getName)).containsExactly(
        "promptSuggestionAction:${first.id}",
        "promptSuggestionAction:${second.id}",
        "promptSuggestionAction:${third.id}",
      )

      actions[1].doClick()

      assertThat(selected).isEqualTo(second)
    }
  }

  private fun candidate(id: String, label: String, promptText: String): AgentPromptSuggestionCandidate {
    return AgentPromptSuggestionCandidate(
      id = id,
      label = label,
      promptText = promptText,
    )
  }
}
