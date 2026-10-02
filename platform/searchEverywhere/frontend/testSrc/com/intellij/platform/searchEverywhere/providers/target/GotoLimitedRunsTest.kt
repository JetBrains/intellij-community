// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.searcheverywhere.GotoLimitedRuns
import com.intellij.util.indexing.FindSymbolParameters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [GotoLimitedRuns], the loop of [SeTargetItemsProvider.collectItems] and of the classic goto contributors: the search
 * runs again with a doubled limit while a contributor reported a cut and the collector took every item.
 */
class GotoLimitedRunsTest {

  @Test
  fun aSpecificQueryRunsOnce() {
    val result = search(honoring(40))
    assertEquals(listOf(50), result.limits)
    assertEquals((0 until 40).toList(), result.rows)
  }

  @Test
  fun exactlyOnePageRunsOnce() {
    // The contributor has 50 items for the limit 50: it has no item 51, so it cut nothing.
    val result = search(honoring(50))
    assertEquals(listOf(50), result.limits)
    assertEquals(50, result.rows.size)
  }

  @Test
  fun aCutContributorRunsUntilItHasNoMore() {
    val result = search(honoring(300))
    assertEquals(listOf(50, 100, 200, 400), result.limits)
    assertEquals((0 until 300).toList(), result.rows, "every item once, in the order of the runs")
  }

  @Test
  fun oneItemMoreThanAPageTakesTwoRuns() {
    val result = search(honoring(51))
    assertEquals(listOf(50, 100), result.limits)
    assertEquals((0 until 51).toList(), result.rows)
  }

  @Test
  fun aContributorThatIgnoresTheLimitRunsOnce() {
    // It reports no cut: its answer is already full.
    val result = search(ignoring(300))
    assertEquals(listOf(50), result.limits)
    assertEquals((0 until 300).toList(), result.rows)
  }

  @Test
  fun twoCutContributorsRunUntilNeitherIsCut() {
    val result = search(honoring(60), honoring(60, offset = 1000))
    assertEquals(listOf(50, 100), result.limits)
    assertEquals(120, result.rows.size)
    assertEquals(120, result.rows.toSet().size, "no item twice")
  }

  @Test
  fun aCutAnswerThinnedByTheFiltersRunsAgain() {
    // 51 raw items, one dropped by the model filters: the collector sees 50, yet the contributor was cut.
    val result = search(honoring(300), keep = { it != 7 })
    assertEquals(listOf(50, 100, 200, 400), result.limits)
    assertEquals((0 until 300).filter { it != 7 }, result.rows)
  }

  @Test
  fun aCutContributorUnderAnOverlappingIgnoringOneSendsEachItemOnce() {
    val result = search(ignoring(150), honoring(300))
    assertEquals(listOf(50, 100, 200, 400), result.limits)
    assertEquals((0 until 300).toList(), result.rows)
  }

  @Test
  fun aRestartedAttemptSendsEachItemOnce() {
    // A restarted read action finds the items of the same run again; the collector already has them.
    val result = search(honoring(40), attempts = { 2 })
    assertEquals(listOf(50), result.limits)
    assertEquals((0 until 40).toList(), result.rows)
  }

  @Test
  fun anItemWhoseSendWasCancelledIsSentByTheRestartedAttempt() {
    val result = search(honoring(40), attempts = { 2 }, cancelledSend = { attempt, item -> attempt == 1 && item == 7 })
    assertEquals(listOf(50), result.limits)
    assertEquals((0 until 40).toList(), result.rows)
  }

  @Test
  fun aRestartedAttemptThatIsNotCutEndsTheRuns() {
    // A write action between the attempts removed items: the first attempt was cut, the second one is not.
    var calls = 0
    val shrinking: (Int, () -> Unit) -> List<Int> = { limit, onCut -> honoring(if (calls++ == 0) 300 else 40)(limit, onCut) }
    val result = search(shrinking, attempts = { limit -> if (limit == 50) 2 else 1 })
    assertEquals(listOf(50), result.limits)
    assertEquals((0 until 51).toList(), result.rows)
  }

  @Test
  fun anItemOfAnEarlierRunFromTwoSourcesIsSentOnce() {
    // Both contributors find the same items: run 2 finds the items of run 1 twice, and its new items twice.
    val result = search(honoring(300), honoring(300))
    assertEquals(listOf(50, 100, 200, 400), result.limits)
    assertEquals((0 until 300).toList(), result.rows)
  }

  @Test
  fun aCopyWithABetterWeightIsSentAgain() {
    // The file provider sends a path match, then the same file again with a qualifier bonus.
    val runs = GotoLimitedRuns(50)
    assertTrue(runs.accept("Foo.java", 10))
    assertFalse(runs.accept("Foo.java", 10), "the same weight")
    assertTrue(runs.accept("Foo.java", 20), "a better weight")
    assertFalse(runs.accept("Foo.java", 15), "a weight below the best sent")
  }

  @Test
  fun aBetterCopyWhoseSendWasCancelledIsSentAgain() {
    val runs = GotoLimitedRuns(50)
    runs.accept("Foo.java", 10)
    runs.accept("Foo.java", 20)
    runs.forget("Foo.java", 20)
    assertTrue(runs.accept("Foo.java", 20))
  }

  @Test
  fun aCollectorThatStopsEndsTheRuns() {
    val result = search(honoring(300), acceptedCount = 10)
    assertEquals(listOf(50), result.limits)
    assertEquals((0 until 10).toList(), result.rows)
  }

  @Test
  fun aCutAtUnlimitedEndsTheRuns() {
    // A buggy contributor that always reports a cut must not spin.
    val alwaysCut: (Int, () -> Unit) -> List<Int> = { _, onCut -> onCut(); listOf(1) }
    val half = FindSymbolParameters.UNLIMITED / 2
    assertEquals(listOf(half, FindSymbolParameters.UNLIMITED), search(alwaysCut, firstLimit = half).limits)
    assertEquals(listOf(FindSymbolParameters.UNLIMITED), search(alwaysCut, firstLimit = FindSymbolParameters.UNLIMITED).limits)
  }

  private class Result(val limits: List<Int>, val rows: List<Int>)

  /**
   * Runs [contributors] like `collectItems` does: each contributor answers the limit of the run and reports its cut to the
   * runs, then the model filters ([keep]) drop items. A run makes [attempts] attempts, like a restarted read action.
   * [cancelledSend] cancels the send of an item in an attempt, like a write action, and that attempt stops.
   * The collector returns false after [acceptedCount] rows.
   */
  private fun search(
    vararg contributors: (limit: Int, onCut: () -> Unit) -> List<Int>,
    keep: (Int) -> Boolean = { true },
    attempts: (limit: Int) -> Int = { 1 },
    cancelledSend: (attempt: Int, item: Int) -> Boolean = { _, _ -> false },
    acceptedCount: Int = Int.MAX_VALUE,
    firstLimit: Int = 50,
  ): Result {
    val runs = GotoLimitedRuns(firstLimit)
    val limits = mutableListOf<Int>()
    val rows = mutableListOf<Int>()
    runs.collect { limit ->
      limits += limit
      (1..attempts(limit)).all { attempt ->
        runs.startAttempt()
        var cancelled = false
        val accepted = contributors.all { contributor ->
          contributor(limit, runs::markCut).asSequence().filter(keep).filter { runs.accept(it, 0) }.all { item ->
            if (cancelledSend(attempt, item)) {
              runs.forget(item, 0)
              cancelled = true
              return@all false
            }
            rows += item
            rows.size < acceptedCount
          }
        }
        accepted || cancelled
      }
    }
    return Result(limits, rows)
  }

  /** A contributor with [count] items that honors the limit: its best `limit` items, plus one and a cut when it has more. */
  private fun honoring(count: Int, offset: Int = 0): (Int, () -> Unit) -> List<Int> = { limit, onCut ->
    if (count > limit) onCut()
    val size = minOf(count.toLong(), limit.toLong() + 1).toInt()
    (offset until offset + size).toList()
  }

  /** A contributor with [count] items that ignores the limit and reports no cut. */
  private fun ignoring(count: Int): (Int, () -> Unit) -> List<Int> = { _, _ -> (0 until count).toList() }
}
