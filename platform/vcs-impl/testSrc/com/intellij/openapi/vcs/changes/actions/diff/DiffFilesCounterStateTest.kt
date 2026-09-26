// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.openapi.ListSelection
import com.intellij.openapi.diff.DiffBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffFilesCounterStateTest {
  @Test
  fun `no files state shows the same text as the empty selection of monolith mode`() {
    assertEquals(DiffFilesCounterState(ListSelection.empty<Any>()).text, DiffFilesCounterState.NO_FILES.text)
  }

  @Test
  fun `no files state shows the no files text`() {
    // The counter has one key, and "No files" is its choice for a count of 0.
    val noFiles = DiffBundle.message("diff.files.count.progress", 0, 0)
    assertEquals(noFiles, DiffFilesCounterState.NO_FILES.text)
  }

  @Test
  fun `no files state has no file`() {
    assertFalse(DiffFilesCounterState.NO_FILES.hasFiles)
    assertTrue(DiffFilesCounterState(changesCount = 1, selectedIndex = 0).hasFiles)
  }

  @Test
  fun `a file state shows a different text`() {
    val noFiles = DiffBundle.message("diff.files.count.progress", 0, 0)
    assertNotEquals(noFiles, DiffFilesCounterState(changesCount = 3, selectedIndex = 1).text)
  }
}
