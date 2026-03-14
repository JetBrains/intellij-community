// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptDraftPersistenceDecisionsTest {
  @Test
  fun suggestionInsertedIntoEmptyDraftRemainsTransient() {
    val initial = restoredTaskPromptDraftState("")

    val updated = applySuggestedPromptToDraftState(initial, "Fix the failing tests.")

    assertThat(updated.liveText).isEqualTo("Fix the failing tests.")
    assertThat(updated.persistedUserText).isEmpty()
    assertThat(updated.hasTransientSuggestion).isTrue()
  }

  @Test
  fun suggestionInsertedOverExistingDraftKeepsPreviousPersistedText() {
    val initial = restoredTaskPromptDraftState("Review the stacktrace")

    val updated = applySuggestedPromptToDraftState(initial, "Fix the failing tests.")

    assertThat(updated.liveText).isEqualTo("Fix the failing tests.")
    assertThat(updated.persistedUserText).isEqualTo("Review the stacktrace")
    assertThat(updated.hasTransientSuggestion).isTrue()
  }

  @Test
  fun userEditAfterSuggestionPromotesCurrentTextToPersistedDraft() {
    val suggestion = applySuggestedPromptToDraftState(
      restoredTaskPromptDraftState("Review the stacktrace"),
      "Fix the failing tests.",
    )

    val updated = applyUserEditToDraftState(suggestion, "Fix the failing tests and explain the root cause.")

    assertThat(updated.liveText).isEqualTo("Fix the failing tests and explain the root cause.")
    assertThat(updated.persistedUserText).isEqualTo("Fix the failing tests and explain the root cause.")
    assertThat(updated.hasTransientSuggestion).isFalse()
  }

  @Test
  fun repeatedSuggestionInsertionsKeepLastUserAuthoredDraft() {
    val initial = restoredTaskPromptDraftState("Review the stacktrace")

    val firstSuggestion = applySuggestedPromptToDraftState(initial, "Fix the failing tests.")
    val updated = applySuggestedPromptToDraftState(firstSuggestion, "Explain the failing tests.")

    assertThat(updated.liveText).isEqualTo("Explain the failing tests.")
    assertThat(updated.persistedUserText).isEqualTo("Review the stacktrace")
    assertThat(updated.hasTransientSuggestion).isTrue()
  }
}
