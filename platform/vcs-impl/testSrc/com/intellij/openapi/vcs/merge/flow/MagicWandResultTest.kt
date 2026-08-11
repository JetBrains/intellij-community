// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.diff.statistics.MagicWandResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MagicWandResultTest {

  private fun result(resolved: Int, unresolvedAfter: Int, newlyResolvedConflicts: Int) =
    computeMagicWandResult(resolved, unresolvedAfter, newlyResolvedConflicts)

  @Test
  fun `nothing resolved is no effect`() {
    assertEquals(MagicWandResult.NO_EFFECT,
                 result(resolved = 0, unresolvedAfter = 5, newlyResolvedConflicts = 0))
  }

  @Test
  fun `no effect wins even if other counters look positive`() {
    // Defensive: a zero-resolve press can never be reported as progress.
    assertEquals(MagicWandResult.NO_EFFECT,
                 result(resolved = 0, unresolvedAfter = 0, newlyResolvedConflicts = 3))
  }

  @Test
  fun `no remaining unresolved changes is fully resolved`() {
    assertEquals(MagicWandResult.FULLY_RESOLVED,
                 result(resolved = 4, unresolvedAfter = 0, newlyResolvedConflicts = 1))
  }

  @Test
  fun `resolving a real conflict without finishing the file is partially resolved`() {
    assertEquals(MagicWandResult.PARTIALLY_RESOLVED,
                 result(resolved = 2, unresolvedAfter = 3, newlyResolvedConflicts = 2))
  }

  @Test
  fun `applying only non-conflicting one-side changes is only sides applied`() {
    assertEquals(MagicWandResult.ONLY_SIDES_APPLIED,
                 result(resolved = 2, unresolvedAfter = 3, newlyResolvedConflicts = 0))
  }

  @Test
  fun `best of session prefers the highest-ranked result regardless of order`() {
    // enum is declared best-to-worst, so the "best" is the lowest ordinal.
    assertEquals(MagicWandResult.FULLY_RESOLVED,
                 bestMagicWandResultOf(MagicWandResult.NO_EFFECT, MagicWandResult.FULLY_RESOLVED))
    assertEquals(MagicWandResult.FULLY_RESOLVED,
                 bestMagicWandResultOf(MagicWandResult.FULLY_RESOLVED, MagicWandResult.NO_EFFECT))
    assertEquals(MagicWandResult.PARTIALLY_RESOLVED,
                 bestMagicWandResultOf(MagicWandResult.ONLY_SIDES_APPLIED, MagicWandResult.PARTIALLY_RESOLVED))
  }

  @Test
  fun `best of session starts from the first result`() {
    assertEquals(MagicWandResult.ONLY_SIDES_APPLIED,
                 bestMagicWandResultOf(null, MagicWandResult.ONLY_SIDES_APPLIED))
  }
}
