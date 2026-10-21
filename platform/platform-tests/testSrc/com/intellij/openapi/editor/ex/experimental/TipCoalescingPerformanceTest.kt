// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.StressTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * A benchmark, not a regression test: it measures what tip coalescing buys and what it costs.
 *
 * Every scenario builds one editing session twice, from the same ops. The `coalesced` history
 * appends every op under one agent, so a burst of typing extends one run. The `control` history
 * lets two agents take turns, so no op extends the run before it. The ops, the units, and the
 * text are the same in both, and only the run count differs. So the ratio of a `coalesced`
 * subtest to its `control` subtest is the effect of coalescing, within one run.
 *
 * [benchmarkSubtest] times every row with the platform benchmark framework. The test prints only
 * what a timing cannot show: the run counts, and the heap that each history holds.
 */
@StressTestApplication
@PerformanceUnitTest
class TipCoalescingPerformanceTest {

  /**
   * A typing session in the shape of a real editing trace: typing bursts at a caret, backspace
   * and Delete-key bursts, caret jumps, and pastes. It measures the append of every op, a full
   * replay of the history, and the heap that the history holds.
   */
  @Test
  fun `a typing session appends, replays, and holds the heap`() {
    val session = Session.generate(Random(20260930), SESSION_OPS)
    val before = heapInUse()
    val coalesced = session.graph(sharedAgent = true)
    val afterCoalesced = heapInUse()
    val control = session.graph(sharedAgent = false)
    val afterControl = heapInUse()

    val expected = session.endText()
    assertEquals(expected, coalesced.replay().string())
    assertEquals(expected, control.replay().string())
    assertTrue(coalesced.runCount() < control.runCount())
    println("=== a typing session: ${session.size()} ops over ${session.startText.length} chars ===")
    println("  %-10s %9s %10s %10s".format("history", "runs", "units/run", "heap"))
    printRow("coalesced", coalesced, afterCoalesced - before)
    printRow("control", control, afterControl - afterCoalesced)

    val rows = listOf(
      Triple("coalesced", true, coalesced),
      Triple("control", false, control),
    )
    for ((label, sharedAgent, graph) in rows) {
      benchmarkSubtest("append, $label", SESSION_PASSES) {
        session.graph(sharedAgent).runCount()
      }
      benchmarkSubtest("replay, $label", REPLAY_PASSES) {
        graph.replay().length()
      }
    }
  }

  /**
   * Two users type one session each, concurrently, over one base text, and then merge. The merge
   * replays only the concurrent region, and the region holds fewer runs when bursts coalesce.
   */
  @Test
  fun `a merge of two concurrent typing sessions`() {
    val random = Random(20260930)
    val left = Session.generate(random, MERGE_OPS)
    val right = Session.generate(random, MERGE_OPS, left.startText)
    println("=== a merge of two sessions of $MERGE_OPS ops each ===")
    println("  %-10s %9s".format("history", "runs"))
    for (sharedAgent in booleanArrayOf(true, false)) {
      val label = if (sharedAgent) "coalesced" else "control"
      val base = DocumentBranch.createBranch(left.startText, agent("base"))
      val leftBranch = left.branch(base, "left", sharedAgent)
      val rightBranch = right.branch(base, "right", sharedAgent)
      val merged = leftBranch.merge(rightBranch)
      val expected = merged.string()
      assertEquals(expected, rightBranch.merge(leftBranch).string())
      println("  %-10s %9d".format(label, merged.graph().runCount()))
      benchmarkSubtest("merge, $label", MERGE_PASSES) {
        leftBranch.merge(rightBranch).length()
      }
    }
  }

  /**
   * The cost of [KEYSTROKES] keystrokes, by the length of the burst they belong to. An extension
   * copies the fragment of the run, so a longer burst copies more per keystroke, up to
   * [EventGraph.MAX_COALESCED_INSERT] characters. The last row types right after a paste
   * of [PASTE_LENGTH] characters, which no keystroke may extend.
   */
  @Test
  fun `a keystroke costs a bounded copy`() {
    println("=== $KEYSTROKES keystrokes, by the burst they belong to ===")
    println("  %-18s %9s".format("burst", "runs"))
    for (burst in BURST_LENGTHS) {
      keystrokeRow("$burst chars", burst, pasteLength = 0)
    }
    keystrokeRow("after a paste", KEYSTROKES, PASTE_LENGTH)
  }

  private fun keystrokeRow(label: String, burst: Int, pasteLength: Int) {
    // Built once, so a timed pass does not pay for the string of the paste.
    val paste = "p".repeat(pasteLength)
    fun build(sharedAgent: Boolean): EventGraph {
      val typist = Typist(sharedAgent)
      if (pasteLength > 0) {
        typist.insert(0, paste)
      }
      // The first keystroke types at the end of the paste. Every later burst starts with a jump
      // to the front, which ends the run before it.
      var caret = pasteLength - 1
      for (keystroke in 0 until KEYSTROKES) {
        caret = if (keystroke > 0 && keystroke % burst == 0) 0 else caret + 1
        typist.insert(caret, "k")
      }
      return typist.graph()
    }
    println("  %-18s %9d".format(label, build(sharedAgent = true).runCount()))
    benchmarkSubtest("keystrokes, $label, coalesced", KEYSTROKE_PASSES) {
      build(sharedAgent = true).runCount()
    }
    benchmarkSubtest("keystrokes, $label, control", KEYSTROKE_PASSES) {
      build(sharedAgent = false).runCount()
    }
  }

  private fun printRow(label: String, graph: EventGraph, heap: Long) {
    val unitsPerRun = graph.size().toDouble() / graph.runCount()
    println("  %-10s %9d %10.1f %7d KB".format(label, graph.runCount(), unitsPerRun, heap / 1024))
  }

  /**
   * Appends at the graph's own version. With [sharedAgent] every op goes under one agent;
   * without it two agents take turns, so no op can extend the run before it.
   */
  private class Typist(private val sharedAgent: Boolean) {
    private var graph = EventGraph.createGraph()
    private val seqs = LongArray(AUTHORS.size)
    private var ops = 0

    fun graph(): EventGraph {
      return graph
    }

    fun insert(offset: Int, fragment: CharSequence) {
      val author = nextAuthor()
      val event = Event.createInsert(AUTHORS[author], takeSeqs(author, fragment.length), offset, fragment)
      graph = graph.append(event, graph.version())
    }

    fun delete(offset: Int, length: Int) {
      val author = nextAuthor()
      val event = Event.createDelete(AUTHORS[author], takeSeqs(author, length), offset, length)
      graph = graph.append(event, graph.version())
    }

    private fun nextAuthor(): Int {
      val author = if (sharedAgent) 0 else ops % AUTHORS.size
      ops++
      return author
    }

    private fun takeSeqs(author: Int, count: Int): Long {
      val seq = seqs[author]
      seqs[author] += count
      return seq
    }
  }

  /**
   * One editing session as plain data, so that both histories replay the same ops and share no
   * object with each other. An insert names its text as a range of [typed].
   */
  private class Session private constructor(
    val startText: String,
    private val typed: String,
    private val kinds: IntArray,
    private val offsets: IntArray,
    private val lengths: IntArray,
    private val textStarts: IntArray,
  ) {
    fun size(): Int {
      return kinds.size
    }

    /**
     * The session appended to a graph that starts with [startText].
     */
    fun graph(sharedAgent: Boolean): EventGraph {
      val typist = Typist(sharedAgent)
      typist.insert(0, startText)
      for (i in kinds.indices) {
        if (kinds[i] == INSERT) {
          typist.insert(offsets[i], typed.subSequence(textStarts[i], textStarts[i] + lengths[i]).toString())
        } else {
          typist.delete(offsets[i], lengths[i])
        }
      }
      return typist.graph()
    }

    /**
     * The session applied to a fork of [base]. Without [sharedAgent], two agents take turns.
     */
    fun branch(base: DocumentBranch, name: String, sharedAgent: Boolean): DocumentBranch {
      val authors = arrayOf(agent("$name-a"), agent("$name-b"))
      var branch = base.fork(authors[0])
      for (i in kinds.indices) {
        if (!sharedAgent) {
          branch = branch.fork(authors[i % 2])
        }
        val op = if (kinds[i] == INSERT) {
          DocumentOp.insertOp(offsets[i], typed.subSequence(textStarts[i], textStarts[i] + lengths[i]).toString())
        } else {
          DocumentOp.deleteOp(offsets[i], lengths[i])
        }
        branch = branch.applyOp(op)
      }
      return branch
    }

    fun endText(): String {
      val text = StringBuilder(startText)
      for (i in kinds.indices) {
        if (kinds[i] == INSERT) {
          text.insert(offsets[i], typed, textStarts[i], textStarts[i] + lengths[i])
        } else {
          text.delete(offsets[i], offsets[i] + lengths[i])
        }
      }
      return text.toString()
    }

    companion object {
      private const val INSERT = 0
      private const val DELETE = 1

      /**
       * A session of [ops] ops. The weights follow the real traces that the coalescing design
       * measured: mostly typing bursts, then backspaces, jumps, Delete-key bursts, and pastes.
       */
      fun generate(
      random: Random,
      ops: Int,
      startText: String = randomText(random, START_LENGTH),
    ): Session {
        val typed = StringBuilder()
        val kinds = IntArray(ops)
        val offsets = IntArray(ops)
        val lengths = IntArray(ops)
        val textStarts = IntArray(ops)
        var length = startText.length
        var caret = 0
        var op = 0
        fun add(kind: Int, offset: Int, count: Int) {
          kinds[op] = kind
          offsets[op] = offset
          lengths[op] = count
          textStarts[op] = typed.length
          op++
        }
        while (op < ops) {
          when (random.nextInt(20)) {
            in 0..10 -> repeat(minOf(1 + random.nextInt(40), ops - op)) {
              add(INSERT, caret, 1)
              typed.append(ALPHABET[random.nextInt(ALPHABET.length)])
              caret++
              length++
            }
            in 11..13 -> repeat(minOf(1 + random.nextInt(8), caret, ops - op)) {
              caret--
              add(DELETE, caret, 1)
              length--
            }
            14 -> repeat(minOf(1 + random.nextInt(8), length - caret, ops - op)) {
              add(DELETE, caret, 1)
              length--
            }
            in 15..18 -> caret = random.nextInt(length + 1)
            else -> {
              val paste = randomText(random, 20 + random.nextInt(200))
              add(INSERT, caret, paste.length)
              typed.append(paste)
              caret += paste.length
              length += paste.length
            }
          }
        }
        return Session(startText, typed.toString(), kinds, offsets, lengths, textStarts)
      }

      private fun randomText(random: Random, length: Int): String {
        val text = StringBuilder(length)
        repeat(length) {
          text.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return text.toString()
      }
    }
  }

  private companion object {
    val AUTHORS = arrayOf(agent("user"), agent("other"))

    const val SESSION_OPS = 20_000
    const val MERGE_OPS = 4_000
    const val START_LENGTH = 2_000
    const val ALPHABET = "abcdefghijklmnopqrstuvwxyz    \n"

    const val KEYSTROKES = 65_536
    val BURST_LENGTHS = intArrayOf(16, EventGraph.MAX_COALESCED_INSERT, 4_096)
    const val PASTE_LENGTH = 1_000_000

    /**
     * The passes of one attempt, per scenario. Each count keeps the faster variant at about 50 ms
     * or more, because the framework reports whole milliseconds.
     */
    const val SESSION_PASSES = 100
    const val REPLAY_PASSES = 50
    const val MERGE_PASSES = 50
    const val KEYSTROKE_PASSES = 32
  }
}
