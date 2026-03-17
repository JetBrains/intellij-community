// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.MapEntry
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

  @Test
  fun extensionSynchronizationUpdatesPersistedAndLiveTextTogetherWhenTheyMatch() {
    val initial = restoredTaskPromptDraftState("Review TEST-1")

    val updated = synchronizeExtensionDraftState(initial, "issues") { prompt ->
      prompt.copy(content = prompt.content.replace("TEST-1", "TEST-2"))
    }

    assertThat(updated.liveText).isEqualTo("Review TEST-2")
    assertThat(updated.persistedUserText).isEqualTo("Review TEST-2")
    assertThat(updated.hasTransientSuggestion).isFalse()
  }

  @Test
  fun extensionSynchronizationPreservesSeparateLiveTextWhenDraftHasTransientEdits() {
    val initial = AgentPromptTaskDraftState(
      liveText = "Review TEST-1 and explain the regression.",
      persistedUserText = "Review TEST-1",
      hasTransientSuggestion = true,
    )

    val updated = synchronizeExtensionDraftState(initial, "issues") { prompt ->
      prompt.copy(content = prompt.content.replace("TEST-1", "TEST-2"))
    }

    assertThat(updated.liveText).isEqualTo("Review TEST-2 and explain the regression.")
    assertThat(updated.persistedUserText).isEqualTo("Review TEST-2")
    assertThat(updated.hasTransientSuggestion).isTrue()
  }

  @Test
  fun extensionDraftPersistenceKeepsSeparateDraftKinds() {
    val taskStates = linkedMapOf(
      "extension:review:default" to restoredTaskPromptDraftState("Default review prompt"),
      "extension:review:issues" to restoredTaskPromptDraftState("Issues review prompt"),
    )

    val persistedDrafts = AgentPromptExtensionDraftDecisions.persistTaskDrafts(
      taskKeyPrefix = "extension:review",
      taskStates = taskStates,
      classifyPromptDraftKind = { promptText -> if (promptText.contains("Issues")) "issues" else "default" },
    )

    assertThat(persistedDrafts).containsOnly(
      MapEntry.entry("extension:review:default", "Default review prompt"),
      MapEntry.entry("extension:review:issues", "Issues review prompt"),
    )
  }

  @Test
  fun extensionDraftPersistenceReclassifiesActiveDraftIntoTargetKind() {
    val taskStates = linkedMapOf(
      "extension:review:default" to restoredTaskPromptDraftState("Older default draft"),
      "extension:review:issues" to restoredTaskPromptDraftState("Rewritten prompt without bug section"),
    )

    val persistedDrafts = AgentPromptExtensionDraftDecisions.persistTaskDrafts(
      taskKeyPrefix = "extension:review",
      taskStates = taskStates,
      classifyPromptDraftKind = { promptText -> if (promptText.contains("issue block")) "issues" else "default" },
    )

    assertThat(persistedDrafts).containsOnly(
      MapEntry.entry("extension:review:default", "Rewritten prompt without bug section"),
    )
  }
}
