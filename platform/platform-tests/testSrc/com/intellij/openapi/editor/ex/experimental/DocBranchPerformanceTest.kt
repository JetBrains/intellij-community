// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

/**
 * A benchmark, not a regression test: it emulates realistic editing histories over a huge file.
 *
 * The history imitates real users. Each user action is one of:
 * - type char by char: every keystroke is its own insert op, and the keystrokes of one burst
 *   extend one run;
 * - autocomplete: one word lands as one insert op;
 * - copy-paste: a medium fragment of the current text lands as one insert op;
 * - move text: one delete op plus one insert op of the same fragment.
 *
 * [benchmarkSubtest] times every scenario with the platform benchmark framework. Every scenario
 * also reports the history size: the units, the runs, and the heap in use. The history grows
 * without a bound, and no timing shows that.
 */
@StressTestApplication
@PerformanceUnitTest
class DocBranchPerformanceTest {

  /**
   * The concurrency varies: the sessions run with 1, 2, 3, 4, and 5 users editing at once. Every
   * user forks from the current base, edits, and merges back. The first merge of a session is a
   * fast-forward; every later one resolves real concurrency with a partial replay from the common
   * ancestor over one lazily-split placeholder. The size ladder shows how that cost grows with the
   * document.
   *
   * One timed pass runs the whole scenario from a fresh base. A pass takes a few milliseconds, so
   * one attempt runs [COLLABORATIVE_PASSES] of them.
   */
  @Test
  fun `a realistic collaborative history over EditorImpl`() {
    val fullText = Files.readString(hugeTextPath())
    for ((index, size) in SIZES.withIndex()) {
      val text = if (size == 0 || size >= fullText.length) fullText else fullText.substring(0, size)
      val final = runScenario(text)
      // The scenario is deterministic, so its counts are exact. A change to the merge or the replay
      // must leave them as they are, and the text must equal a full replay of the history.
      assertEquals(FINAL_LENGTHS[index], final.text().length()) { "the final length over ${text.length} chars" }
      assertEquals(HISTORY_UNITS[index], final.graph().size()) { "the units over ${text.length} chars" }
      assertEquals(HISTORY_RUNS[index], final.graph().runCount()) { "the runs over ${text.length} chars" }
      assertEquals(final.graph().replay().string(), final.string()) { "the replay over ${text.length} chars" }
      println("=== the collaborative scenario over ${text.length} chars ===")
      println("  final: ${final.text().length()} chars")
      reportHistory(final)
      benchmarkSubtest("collaborative history over ${text.length} chars", COLLABORATIVE_PASSES) {
        runScenario(text).text().length()
      }
    }
  }

  /**
   * The base case: one user edits the huge file alone, with no forks and no merges.
   *
   * The session is recorded once, and its ops then replay in the timed subtests. Applied to a fresh
   * branch and to a plain [DocumentText], they give the price per op of the history and the mod state.
   * The first and the last [FLAT_COST_BATCHES] batches show whether the cost of an op grows with the
   * history. Each of them applies to a fresh branch that holds the batches before it.
   */
  @Test
  fun `a single user edits EditorImpl`() {
    val text = Files.readString(hugeTextPath())
    val random = Random(20260827)
    val recorded = ArrayList<DocumentOp.Text>()
    val batchEnds = IntArray(SINGLE_USER_BATCHES)
    val user = User(DocumentBranch.createBranch(text, agent("user")))
    user.recorder = recorded
    for (batch in 0 until SINGLE_USER_BATCHES) {
      repeat(SINGLE_USER_ACTIONS_PER_BATCH) {
        performAction(user, random)
      }
      batchEnds[batch] = recorded.size
    }
    println("=== one user edits ${text.length} chars ===")
    println("  total: ${recorded.size} ops, ${user.branch.text().length()} chars")
    reportHistory(user.branch)

    // The history-tracking branch and the plain text agree on every op.
    val plain = applied(DocumentText.createText(text), recorded)
    assertEquals(plain.string(), applied(freshBranch(text), recorded, 0, recorded.size).string())
    assertEquals(plain.string(), user.branch.string())
    assertEquals(plain.string(), appliedWithTrims(freshBranch(text), recorded).string())

    benchmarkSubtest("apply-only, DocBranch") {
      applied(freshBranch(text), recorded, 0, recorded.size).length()
    }
    benchmarkSubtest("apply-only, plain DocumentText") {
      applied(DocumentText.createText(text), recorded).length()
    }
    benchmarkSubtest("apply-only, DocBranch trimmed after each op") {
      appliedWithTrims(freshBranch(text), recorded).length()
    }

    val firstEnd = batchEnds[FLAT_COST_BATCHES - 1]
    val lastStart = batchEnds[SINGLE_USER_BATCHES - FLAT_COST_BATCHES - 1]
    lateinit var start: DocumentBranch
    benchmarkSubtest("the first $FLAT_COST_BATCHES batches", setup = { start = freshBranch(text) }) {
      applied(start, recorded, 0, firstEnd).length()
    }
    val lastSetup = { start = applied(freshBranch(text), recorded, 0, lastStart) }
    benchmarkSubtest("the last $FLAT_COST_BATCHES batches", setup = lastSetup) {
      applied(start, recorded, lastStart, recorded.size).length()
    }
  }

  /**
   * The collaborative scenario over [text]. Every session forks, edits, and merges back.
   */
  private fun runScenario(text: String): DocumentBranch {
    val random = Random(20260827)
    var base = DocumentBranch.createBranch(text, agent("base"))
    for (level in CONCURRENCY_LEVELS) {
      // `level` users fork from the current base and edit concurrently.
      val users = List(level) { i -> User(base.fork(agent("user$i"))) }
      for (user in users) {
        repeat(ACTIONS_PER_USER) {
          performAction(user, random)
        }
      }
      for (user in users) {
        base = base.merge(user.branch)
      }
    }
    return base
  }

  private fun freshBranch(text: String): DocumentBranch {
    return DocumentBranch.createBranch(text, agent("user"))
  }

  /**
   * [branch] with the ops `[from, until)` of [ops] applied.
   */
  private fun applied(
    branch: DocumentBranch,
    ops: List<DocumentOp.Text>,
    from: Int,
    until: Int,
  ): DocumentBranch {
    var result = branch
    for (i in from until until) {
      result = result.applyOp(ops[i])
    }
    return result
  }

  /**
   * [branch] with every op of [ops] applied, and a trim to the version after each one. That is the
   * history of a document when nothing holds an older version.
   */
  private fun appliedWithTrims(branch: DocumentBranch, ops: List<DocumentOp.Text>): DocumentBranch {
    var result = branch
    for (op in ops) {
      val edited = result.applyOp(op)
      result = edited.trimmedTo(edited.version())
    }
    return result
  }

  private fun applied(text: DocumentText, ops: List<DocumentOp.Text>): DocumentText {
    var result = text
    for (op in ops) {
      result = result.applyOp(op)
    }
    return result
  }

  // ---------------------------------------------------------------------------- the user model

  private class User(var branch: DocumentBranch) {
    var caret = 0
    var recorder: ArrayList<DocumentOp.Text>? = null

    fun apply(op: DocumentOp.Text) {
      branch = branch.applyOp(op)
      recorder?.add(op)
    }
  }

  /**
   * Performs one random user action.
   *
   * The bounds weight the four actions 40, 25, 15, and 9. Keep the bound at 89. Another
   * bound draws a different history, so the measured times no longer compare.
   */
  private fun performAction(user: User, random: Random) {
    when (random.nextInt(89)) {
      in 0..39 -> typeChars(user, random)
      in 40..64 -> autocompleteWord(user, random)
      in 65..79 -> copyPasteFragment(user, random)
      else -> moveFragment(user, random)
    }
  }

  private fun moveCaret(user: User, random: Random) {
    user.caret = random.nextInt(user.branch.text().length() + 1)
  }

  private fun typeChars(user: User, random: Random) {
    moveCaret(user, random)
    repeat(5 + random.nextInt(25)) {
      user.apply(insertOp(user.caret, TYPED[random.nextInt(TYPED.length)].toString()))
      user.caret++
    }
  }

  private fun autocompleteWord(user: User, random: Random) {
    moveCaret(user, random)
    val word = COMPLETIONS[random.nextInt(COMPLETIONS.size)]
    user.apply(insertOp(user.caret, word))
    user.caret += word.length
  }

  private fun copyPasteFragment(user: User, random: Random) {
    val length = user.branch.text().length()
    if (length < 200) {
      return
    }
    val fragmentLength = minOf(100 + random.nextInt(301), length / 2)
    val from = random.nextInt(length - fragmentLength + 1)
    val fragment = user.branch.text().string(from, from + fragmentLength)
    moveCaret(user, random)
    user.apply(insertOp(user.caret, fragment))
    user.caret += fragment.length
  }

  private fun moveFragment(user: User, random: Random) {
    val length = user.branch.text().length()
    if (length < 100) {
      return
    }
    val fragmentLength = minOf(30 + random.nextInt(121), length / 2)
    val from = random.nextInt(length - fragmentLength + 1)
    val fragment = user.branch.text().string(from, from + fragmentLength)
    user.apply(deleteOp(from, fragmentLength))
    val to = random.nextInt(user.branch.text().length() + 1)
    user.apply(insertOp(to, fragment))
    user.caret = to + fragment.length
  }

  // -------------------------------------------------------------------------------- utilities

  /**
   * Reports what the history costs. The timings alone cannot show this, and the history
   * is what grows without a bound.
   *
   * The unit and run counts are exact and repeat run to run, so they compare directly.
   * The run count is the one that tip coalescing changes: a burst of typing makes one run and
   * not one per keystroke. The heap number is a hint only (see [heapInUse]), but it gives the
   * order of the retained size.
   */
  private fun reportHistory(branch: DocumentBranch) {
    val graph = branch.graph()
    val perRun = graph.size().toDouble() / maxOf(1, graph.runCount())
    val usedMb = heapInUse() / (1024 * 1024)
    println(
      "  history: ${graph.size()} units, ${graph.runCount()} runs " +
      "(${String.format("%.1f", perRun)} units per run), heap in use ~$usedMb MB"
    )
  }

  private fun hugeTextPath(): Path {
    return Path.of(PathManager.getCommunityHomePath(), TEST_DATA, "EditorImpl.java.txt")
  }

  companion object {
    /**
     * The test data of the feature, under the community root.
     */
    private const val TEST_DATA = "platform/platform-tests/testData/editor/docBranch"

    /**
     * The document sizes to run; 0 means the whole file.
     */
    private val SIZES = intArrayOf(25_000, 100_000, 0)

    /**
     * The exact results of the collaborative scenario, one per entry of [SIZES]. The base text
     * is no event, so the history is the same for every size.
     */
    private val FINAL_LENGTHS = intArrayOf(30_801, 105_801, 244_567)
    private val HISTORY_UNITS = intArrayOf(7_733, 7_733, 7_733)
    private val HISTORY_RUNS = intArrayOf(98, 98, 98)

    /**
     * The number of users that edit at once, session by session.
     */
    private val CONCURRENCY_LEVELS = intArrayOf(1, 2, 3, 4, 5)

    private const val ACTIONS_PER_USER = 6

    /**
     * The passes of one collaborative attempt. The framework reports whole milliseconds.
     */
    private const val COLLABORATIVE_PASSES = 200
    private const val SINGLE_USER_BATCHES = 1000
    private const val SINGLE_USER_ACTIONS_PER_BATCH = 200

    /**
     * The batches at each end of the single-user session that the flat-cost subtests apply.
     */
    private const val FLAT_COST_BATCHES = 100
    private const val TYPED = "abcdefghijklmnopqrstuvwxyz    ();.{}\n"
    private val COMPLETIONS = arrayOf(
      "getDocument()",
      "invokeLater",
      "myScrollingModel",
      "CaretVisualAttributes",
      "repaintCaretRegion(caret)",
      "EditorImpl",
      "isReleased",
      "assertIsDispatchThread()",
    )
  }
}
