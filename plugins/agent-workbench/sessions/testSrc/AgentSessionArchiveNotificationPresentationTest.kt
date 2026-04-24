// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.service.buildArchiveNotificationPresentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionArchiveNotificationPresentationTest {
  @Test
  fun singleArchivedThreadUsesNamedBodyWhenLabelAvailable() {
    val presentation = buildArchiveNotificationPresentation(
      requestedCount = 1,
      archivedCount = 1,
      singleArchivedLabel = "Refactor session setup",
      canUndo = true,
    )

    assertThat(presentation.title).isEqualTo("Thread archived")
    assertThat(presentation.body).isEqualTo("Refactor session setup")
    assertThat(presentation.showUndoAction).isTrue()
  }

  @Test
  fun singleArchivedThreadFallsBackToGenericBodyWhenLabelMissing() {
    val presentation = buildArchiveNotificationPresentation(
      requestedCount = 1,
      archivedCount = 1,
      singleArchivedLabel = null,
      canUndo = false,
    )

    assertThat(presentation.title).isEqualTo("Thread archived")
    assertThat(presentation.body).isEqualTo("Archived 1 thread.")
    assertThat(presentation.showUndoAction).isFalse()
  }

  @Test
  fun multiArchiveUsesCountBodyWhenAllTargetsSucceed() {
    val presentation = buildArchiveNotificationPresentation(
      requestedCount = 3,
      archivedCount = 3,
      singleArchivedLabel = null,
      canUndo = true,
    )

    assertThat(presentation.title).isEqualTo("Threads archived")
    assertThat(presentation.body).isEqualTo("Archived 3 threads.")
    assertThat(presentation.showUndoAction).isTrue()
  }

  @Test
  fun partialArchiveUsesRequestedAndArchivedCounts() {
    val presentation = buildArchiveNotificationPresentation(
      requestedCount = 3,
      archivedCount = 1,
      singleArchivedLabel = "Ignored",
      canUndo = false,
    )

    assertThat(presentation.title).isEqualTo("Threads archived")
    assertThat(presentation.body).isEqualTo("Archived 1 of 3 threads.")
    assertThat(presentation.showUndoAction).isFalse()
  }
}
