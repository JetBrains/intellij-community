// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * A gossip fuzz: replicas edit and pull from each other in a random order, so the merge
 * base moves in every direction. [DocBranchFuzzTest] keeps a fork-then-sync shape with
 * small ops. This test adds the shapes that shape misses. They are many pulls in any direction,
 * whole-document deletes, pastes of up to 12 characters, and text moves. Long runs and many agents
 * are the job of [EgWalkerConformanceTest].
 *
 * Each replica also has a caret, and most edits type or delete at it. Those edits extend the
 * newest run. So a pull often lands in the middle of a run, and two replicas often cut the
 * runs of one agent in different places.
 *
 * These invariants hold after every merge:
 * - the branch text equals a from-scratch replay of the merged graph;
 * - the ops of the merge fold the text of the receiver into the merged text;
 * - every op with a move offset is half of a valid move pair;
 * - a full sync brings every replica to one text.
 *
 * After the sync, a past version of a replica replays to the text it held, in another replica too.
 */
class DocBranchGossipFuzzTest {

  @Test
  fun `gossiping replicas converge and match a replay`() {
    repeat(ROUNDS) { round ->
      fuzzRound(20260828L, round) { random ->
        val replicaCount = 2 + random.nextInt(4)
        val base = DocBranch.createBranch(randomText(random), agent("base"))
        val replicas = ArrayList<DocBranch>()
        for (i in 0 until replicaCount) {
          replicas.add(base.fork(agent("agent$i")))
        }

        // Per replica: the versions it passed through, with the text it held there. A version
        // names its heads by event id, so it stays valid in every replica that merges this one.
        val history = ArrayList<ArrayList<Pair<Version, String>>>()
        for (replica in replicas) {
          history.add(arrayListOf(replica.graph().version() to replica.string()))
        }
        val carets = IntArray(replicas.size)

        repeat(STEPS) { step ->
          val i = random.nextInt(replicas.size)
          if (random.nextInt(3) == 0) {
            val j = random.nextInt(replicas.size)
            if (i != j) {
              val merge = replicas[i].mergeWithOps(replicas[j])
              val merged = merge.branch()
              assertEquals(merged.graph().replay().string(), merged.string()) {
                "round $round, step $step, merge $i <- $j"
              }
              checkOps(replicas[i], merge) { "round $round, step $step, the ops of merge $i <- $j" }
              replicas[i] = merged
            }
          } else if (random.nextInt(8) == 0) {
            val move = randomMoveOps(random, replicas[i].text().chars())
            replicas[i] = move.fold(replicas[i]) { branch, op -> branch.applyOp(op) }
          } else {
            val op = randomOp(random, replicas[i].length(), carets[i])
            replicas[i] = replicas[i].applyOp(op)
            carets[i] = if (op is DocumentOp.Insert) op.offset() + op.length() else op.offset()
          }
          history[i].add(replicas[i].graph().version() to replicas[i].string())
        }

        // A full sync: every replica pulls every other one, until nothing changes.
        repeat(2) {
          for (i in replicas.indices) {
            for (j in replicas.indices) {
              if (i != j) {
                val merge = replicas[i].mergeWithOps(replicas[j])
                checkOps(replicas[i], merge) { "round $round, the ops of sync $i <- $j" }
                replicas[i] = merge.branch()
              }
            }
          }
        }
        val expected = replicas[0].string()
        for (i in replicas.indices) {
          assertEquals(expected, replicas[i].string()) { "round $round, replica $i after the sync" }
          assertEquals(replicas[i].graph().replay().string(), replicas[i].string()) {
            "round $round, replica $i replay"
          }
          // Every past version of this replica and of the next one still replays to the text
          // it held there, out of the much larger merged graph.
          val graph = replicas[i].graph()
          val next = (i + 1) % replicas.size
          for ((at, text) in history[i] + history[next]) {
            assertEquals(text, graph.replay(at).string()) { "round $round, replica $i at $at" }
          }
        }
        assertSameText(DocumentText.createText(expected), replicas[0].text())
      }
    }
  }

  private fun randomText(random: Random): String {
    val text = StringBuilder()
    repeat(random.nextInt(BASE_LENGTH_BOUND)) {
      text.append(ALPHABET[random.nextInt(ALPHABET.length)])
    }
    return text.toString()
  }

  /**
   * The next edit of a replica whose caret is at [caret]. Most edits type one character or
   * press the Delete key at the caret. The rest jump anywhere, as [randomJump] describes. A
   * merge can shorten the text, so the caret is clamped first.
   */
  private fun randomOp(random: Random, length: Int, caret: Int): DocumentOp.Text {
    val at = minOf(caret, length)
    val roll = random.nextInt(10)
    if (roll < 4) {
      return insertOp(at, ALPHABET[random.nextInt(ALPHABET.length)].toString())
    }
    if (roll < 5 && at < length) {
      return deleteOp(at, 1)
    }
    if (roll < 6 && at > 0) {
      // A backspace.
      return deleteOp(at - 1, 1)
    }
    return randomJump(random, length)
  }

  /**
   * Fails unless the ops of [merge] fold the text of [receiver] into the merged text, none of them is
   * empty, and every move pair among them is valid. A fold through an op outside its text throws on
   * its own.
   */
  private fun checkOps(receiver: DocBranch, merge: DocMerge, where: () -> String) {
    val ops = merge.ops()
    for (op in ops) {
      assertTrue(op.length() > 0, where)
    }
    assertEquals(merge.branch().string(), receiver.text().afterOps(ops).string(), where)
    assertMovePairs(receiver.text(), ops)
  }

  /**
   * An edit anywhere in the text: a keystroke or a paste, a small delete or a wipe.
   */
  private fun randomJump(random: Random, length: Int): DocumentOp.Text {
    if (length == 0 || random.nextInt(10) < 6) {
      // A mix of single keystrokes and big pastes.
      val fragmentLength = if (random.nextInt(5) == 0) 1 + random.nextInt(12) else 1 + random.nextInt(2)
      val fragment = StringBuilder()
      repeat(fragmentLength) {
        fragment.append(ALPHABET[random.nextInt(ALPHABET.length)])
      }
      return insertOp(random.nextInt(length + 1), fragment.toString())
    }
    // A mix of small deletes and a wipe of the whole rest of the text.
    val offset = random.nextInt(length)
    val opLength = if (random.nextInt(6) == 0) length - offset else 1 + random.nextInt(minOf(4, length - offset))
    return deleteOp(offset, opLength)
  }

  companion object {
    private const val ROUNDS = 4000
    private const val STEPS = 30
    private const val ALPHABET = "abcde \n\r"

    /**
     * The base text is shorter than this.
     */
    private const val BASE_LENGTH_BOUND = 10
  }
}
