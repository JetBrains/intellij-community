// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diff.comparison.CancellationChecker
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import com.intellij.openapi.editor.ex.experimental.benchmarkSubtest
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * A benchmark, not a regression test: it runs [DocTextDiff] over a set of realistic document changes
 * and reports the cost of each one.
 *
 * Each scenario reports four numbers.
 * - The time of the diff, and the time to apply its script to the base. [benchmarkSubtest] measures
 *   both with the platform benchmark framework. Divide a time by the passes in its subtest name.
 * - The op count. Every op becomes an [com.intellij.openapi.editor.ex.experimental.Event] that the
 *   graph keeps forever, so this is the per-event memory.
 * - The unit count, which is the number of characters that the ops insert or delete. A unit is what
 *   a merge walks and what the graph stores per character, so this is the number that decides
 *   whether a script is cheap or ruinous.
 *
 * Time alone hides the interesting failures. A script that runs in 1 ms and emits two million units
 * is far worse for the event graph than one that runs in 300 ms and emits half a million.
 *
 * Every scenario also applies its script back to the base and checks the text, so the benchmark
 * doubles as a correctness run over inputs that the functional tests never reach.
 */
@StressTestApplication
@PerformanceUnitTest
class DocTextDiffPerformanceTest {

  @Test
  fun `the diff over a realistic set of document changes`() {
    val unit = Files.readString(hugeTextPath())
    for (copies in COPIES) {
      val text = buildString { repeat(copies) { append(unit) } }
      val base = DocumentText.createText(text)
      val label = if (copies == 1) "the source file" else "the source file $copies times over"
      println("=== $label: ${base.length()} chars, ${base.lineCount()} lines ===")
      printHeader()
      for (scenario in scenarios(base, text)) {
        if (copies > 1 && !scenario.scalesUp) {
          continue
        }
        run("$label, ${scenario.name}", base, DocumentText.createText(scenario.target()), scenario.passes)
      }
    }
  }

  /**
   * A minified document holds the whole text on one line, so the growth to whole lines gives up and
   * the character region stands on its own. The interesting question is whether the cost tracks the
   * edit or the document.
   */
  @Test
  fun `the diff over one huge line`() {
    val oneLine = Files.readString(hugeTextPath()).replace('\n', ' ')
    val base = DocumentText.createText(oneLine)
    println("=== one line of ${base.length()} chars ===")
    printHeader()
    run("one line, one edit in the middle", base, DocumentText.createText(edited(oneLine, 1)), passes = 100)
    run("one line, 20 edits", base, DocumentText.createText(edited(oneLine, 20)), passes = 5)
    run("one line, 500 edits", base, DocumentText.createText(edited(oneLine, 500)), passes = 2)
  }

  private fun scenarios(base: DocumentText, text: String): List<Scenario> = listOf(
    // The reload cases. A document comes back from disk with a few changes.
    Scenario("no change", passes = 80) { text },
    Scenario("append one line", passes = 80) { "$text  // appended\n" },
    Scenario("delete the first 200 lines", passes = 80) { text.substring(base.lineStartOffset(200)) },
    Scenario("one edit inside one line", passes = 600) { text.replaceFirst("myScrollingModel", "theScrollingModel") },
    Scenario("20 new lines, scattered", passes = 40) { withNewLines(base, 20) },
    Scenario("500 new lines, scattered", passes = 40) { withNewLines(base, 500) },

    // The tool cases. Something rewrote the file.
    Scenario("rename an identifier everywhere", passes = 50) { text.replace("myScrollingModel", "theScrollingModel") },
    Scenario("reindent every line", passes = 4) { text.replace("    ", "\t") },

    // The cases that punish the algorithm. They stay at the smallest size on purpose.
    Scenario("convert every line to CRLF", passes = 4, scalesUp = false) { text.replace("\n", "\r\n") },
    Scenario("reverse the line order", passes = 1, scalesUp = false) { text.split("\n").asReversed().joinToString("\n") },
    Scenario("replace the whole text", passes = 1, scalesUp = false) { unrelatedText(text.length) },
  )

  /**
   * Diffs [base] against [target], checks that the script rebuilds [target], and prints the op and
   * unit counts. Then it times the diff, [passes] times per attempt.
   *
   * It times the apply only for a script of [MIN_TIMED_APPLY_OPS] ops or more, because a shorter
   * one applies in microseconds. An attempt then applies about [APPLY_OPS_PER_ATTEMPT] ops in all.
   */
  private fun run(name: String, base: DocumentText, target: DocumentText, passes: Int) {
    val ops = DocTextDiff.diff(base, target, CancellationChecker.EMPTY)
    assertEquals(target.string(), applied(base, ops).string()) { "the script does not rebuild the target of \"$name\"" }
    val units = units(ops)
    val share = 100.0 * units / maxOf(base.length(), 1)
    val applyPasses = if (ops.size < MIN_TIMED_APPLY_OPS) 0 else Math.ceilDiv(APPLY_OPS_PER_ATTEMPT, ops.size)
    val applyColumn = if (applyPasses == 0) "not timed" else "$applyPasses"
    println("  %-56s%9d ops%11d units%8.1f%%%8d%12s".format(name, ops.size, units, share, passes, applyColumn))
    benchmarkSubtest("diff, $name", passes) {
      DocTextDiff.diff(base, target, CancellationChecker.EMPTY).size
    }
    if (applyPasses > 0) {
      benchmarkSubtest("apply, $name", applyPasses) {
        applied(base, ops).length()
      }
    }
  }

  private fun applied(base: DocumentText, ops: List<DocumentOp.Text>): DocumentText {
    var result = base
    for (op in ops) {
      result = result.applyOp(op)
    }
    return result
  }

  private fun printHeader() {
    println("  %-56s%13s%17s%9s%8s%12s".format("scenario", "ops", "units", "of doc", "passes", "apply"))
  }

  /**
   * The number of characters that [ops] insert or delete. One character is one unit of the graph.
   */
  private fun units(ops: List<DocumentOp.Text>): Int {
    var count = 0
    for (op in ops) {
      count += when (op) {
        is DocumentOp.Insert -> op.fragment().length
        is DocumentOp.Delete -> op.length()
      }
    }
    return count
  }

  /**
   * [base] with [count] new lines, spread evenly over the document.
   */
  private fun withNewLines(base: DocumentText, count: Int): String {
    var target = base
    val step = maxOf(base.lineCount() / (count + 1), 1)
    // Back to front, so every offset indexes the base.
    for (index in count downTo 1) {
      target = target.insert(base.lineStartOffset(index * step), "    // edit $index\n")
    }
    return target.string()
  }

  /**
   * [text] with [count] tokens inserted, spread evenly. The text needs no line feed.
   */
  private fun edited(text: String, count: Int): String {
    val step = text.length / (count + 1)
    val builder = StringBuilder(text)
    for (index in count downTo 1) {
      builder.insert(index * step, " /*$index*/ ")
    }
    return builder.toString()
  }

  /**
   * A text of about [length] characters that shares no line with the source.
   */
  private fun unrelatedText(length: Int): String {
    val builder = StringBuilder(length + 32)
    var line = 0
    while (builder.length < length) {
      builder.append("an unrelated line of content, number ").append(line++).append('\n')
    }
    return builder.toString()
  }

  private fun hugeTextPath(): Path {
    return Path.of(PathManager.getCommunityHomePath(), "platform/platform-tests/testData/editor/docBranch/EditorImpl.java.txt")
  }

  /**
   * One document change. [passes] keeps one attempt over the source file at about 60 ms or more,
   * because the framework reports whole milliseconds. A copy of the file costs more per pass, so
   * it takes the same passes and a longer attempt.
   */
  private class Scenario(val name: String, val passes: Int, val scalesUp: Boolean = true, val target: () -> String)

  companion object {
    /**
     * How many times the source file repeats. The repeat makes every line a duplicate, which the
     * comparison finds harder than a real file of the same size.
     */
    private val COPIES = intArrayOf(1, 10)

    /**
     * The shortest script whose apply is timed, and the ops that one apply attempt runs.
     */
    private const val MIN_TIMED_APPLY_OPS = 20
    private const val APPLY_OPS_PER_ATTEMPT = 20_000
  }
}
