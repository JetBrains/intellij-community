// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.random.asKotlinRandom

class DocBranchFuzzTest {

  @Test
  fun `replicas converge after a full sync`() {
    repeat(ROUNDS) { round ->
      fuzzRound(20260827L, round) { random ->
        val base = DocumentBranch.createBranch(randomText(random), agent("base"))
        val baseVersion = base.graph().version()

        // Every replica forks from the base and edits under its own agent.
        val replicas = ArrayList<DocumentBranch>()
        val replicaCount = 2 + random.nextInt(2)
        for (i in 0 until replicaCount) {
          var replica = base.fork(agent("agent$i"))
          repeat(1 + random.nextInt(6)) {
            replica = replica.applyOp(randomOp(random, replica.length()))
          }
          replicas.add(replica)
        }

        // A full sync in different orders must converge to one text.
        val forward = replicas.reduce { acc, replica -> acc.merge(replica) }
        val backward = replicas.asReversed().reduce { acc, replica -> acc.merge(replica) }
        val shuffledReplicas = ArrayList(replicas)
        shuffledReplicas.shuffle(random.asKotlinRandom())
        val shuffled = shuffledReplicas.reduce { acc, replica -> acc.merge(replica) }
        assertEquals(forward.string(), backward.string()) { "round $round" }
        assertEquals(forward.string(), shuffled.string()) { "round $round, shuffled order" }

        // The materialized text matches a from-scratch replay of the merged graph.
        assertEquals(forward.graph().replay().string(), forward.string()) { "round $round" }

        // A replay of the merged graph at the base version returns the base text.
        assertEquals(base.string(), forward.graph().replay(baseVersion).string()) { "round $round, base version" }

        // The text and the line data match a fresh DocumentText over the same chars.
        assertSameText(DocumentText.createText(forward.string()), forward.text())

        // A second generation: edit after the merge, then merge again.
        val left = forward.applyOp(randomOp(random, forward.length()))
        val right = forward.fork(agent("second")).applyOp(randomOp(random, forward.length()))
        val leftRight = left.merge(right)
        val rightLeft = right.merge(left)
        assertEquals(leftRight.string(), rightLeft.string()) { "round $round, second generation" }
        assertSameText(DocumentText.createText(leftRight.string()), leftRight.text())

        // A pull-based staircase: the common ancestor climbs with every pull.
        var x = leftRight
        var y = leftRight.fork(agent("stair"))
        repeat(3) {
          x = x.applyOp(randomOp(random, x.length()))
          y = y.applyOp(randomOp(random, y.length()))
          if (random.nextBoolean()) {
            x = x.merge(y)
          } else {
            y = y.merge(x)
          }
        }
        val stairForward = x.merge(y)
        val stairBackward = y.merge(x)
        assertEquals(stairForward.string(), stairBackward.string()) { "round $round, staircase" }
        assertEquals(stairForward.graph().replay().string(), stairForward.string()) { "round $round, staircase replay" }
        assertSameText(DocumentText.createText(stairForward.string()), stairForward.text())
      }
    }
  }

  private fun randomText(random: Random): String {
    val length = random.nextInt(12)
    val text = StringBuilder()
    repeat(length) {
      text.append(ALPHABET[random.nextInt(ALPHABET.length)])
    }
    return text.toString()
  }

  private fun randomOp(random: Random, length: Int): DocumentOp.Text {
    if (length == 0 || random.nextBoolean()) {
      val offset = random.nextInt(length + 1)
      val fragment = StringBuilder()
      repeat(1 + random.nextInt(3)) {
        fragment.append(ALPHABET[random.nextInt(ALPHABET.length)])
      }
      return insertOp(offset, fragment.toString())
    }
    val offset = random.nextInt(length)
    val opLength = 1 + random.nextInt(minOf(3, length - offset))
    return deleteOp(offset, opLength)
  }

  companion object {
    private const val ROUNDS = 5000
    private const val ALPHABET = "abcdef \n\r"
  }
}
