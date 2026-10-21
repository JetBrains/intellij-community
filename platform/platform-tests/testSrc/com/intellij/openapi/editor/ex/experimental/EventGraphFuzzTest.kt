// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class EventGraphFuzzTest {

  /**
   * Builds one random linear history three times. `whole` has one event per logical op.
   * `pieces` appends the same units as random smaller pieces. `partial` is a copy of `pieces`
   * frozen at a random earlier unit. Every encoding must replay to the
   * [StringBuilder] model, and every merge across the encodings must change nothing
   * except catching `partial` up. A `partial` merge crosses run boundaries, so it
   * exercises the run-split path of `mergeFrom`.
   *
   * The pieces of one op continue each other, so the append coalesces them back into a run.
   * The run boundaries of the two encodings still differ where an insert is longer than
   * [EventGraph.MAX_COALESCED_INSERT]. One event may pass that limit, but a run that grows
   * piece by piece stops at it.
   */
  @Test
  fun `run boundaries do not change the document`() {
    var coalescedRounds = 0
    var recutRounds = 0
    repeat(ROUNDS) { round ->
      fuzzRound(20260827L, round) { random ->
        val u = agent("u")
        // Both encodings grow from one new graph, so they merge.
        val origin = EventGraph.createGraph()
        var whole = origin
        var pieces = origin
        var partial: EventGraph? = null
        val stopAfterPieces = random.nextInt(12)
        var pieceCount = 0
        val text = StringBuilder()
        var seq = 0L

        repeat(1 + random.nextInt(6)) {
          if (text.isEmpty() || random.nextBoolean()) {
            val pos = random.nextInt(text.length + 1)
            val content = randomString(random)
            whole = whole.append(Event.createInsert(u, seq, pos, content), whole.version())
            var offset = 0
            while (offset < content.length) {
              val piece = 1 + random.nextInt(content.length - offset)
              if (pieceCount == stopAfterPieces) {
                partial = pieces
              }
              pieces = pieces.append(
                Event.createInsert(u, seq + offset, pos + offset, content.substring(offset, offset + piece)),
                pieces.version(),
              )
              pieceCount++
              offset += piece
            }
            text.insert(pos, content)
            seq += content.length
          } else {
            val pos = random.nextInt(text.length)
            val length = 1 + random.nextInt(minOf(3, text.length - pos))
            whole = whole.append(Event.createDelete(u, seq, pos, length), whole.version())
            var offset = 0
            while (offset < length) {
              val piece = 1 + random.nextInt(length - offset)
              if (pieceCount == stopAfterPieces) {
                partial = pieces
              }
              pieces = pieces.append(Event.createDelete(u, seq + offset, pos, piece), pieces.version())
              pieceCount++
              offset += piece
            }
            text.delete(pos, pos + length)
            seq += length
          }
        }

        val expected = text.toString()
        assertEquals(expected, whole.replay().string()) { "round $round, one event per op" }
        assertEquals(expected, pieces.replay().string()) { "round $round, pieces" }
        assertEquals(whole.size(), pieces.size()) { "round $round" }
        if (pieces.runCount() < pieceCount) {
          coalescedRounds++
        }
        if (pieces.runCount() != whole.runCount()) {
          recutRounds++
        }

        // The same units with different run boundaries merge into the same document.
        assertEquals(expected, whole.mergeFrom(pieces).replay().string()) { "round $round, whole + pieces" }
        assertEquals(expected, pieces.mergeFrom(whole).replay().string()) { "round $round, pieces + whole" }

        // A partly caught-up replica catches up from either encoding.
        val caughtUp = partial ?: pieces
        assertEquals(expected, caughtUp.mergeFrom(whole).replay().string()) { "round $round, partial + whole" }
        assertEquals(expected, caughtUp.mergeFrom(pieces).replay().string()) { "round $round, partial + pieces" }
      }
    }
    // The fuzz did both jobs: pieces coalesced, and the two encodings still cut some runs apart.
    assertTrue(coalescedRounds > ROUNDS / 2) { "only $coalescedRounds rounds coalesced a piece" }
    assertTrue(recutRounds > 0) { "no round cut its runs differently" }
  }

  /**
   * Mostly a few characters, and sometimes an insert longer than the coalescing limit.
   */
  private fun randomString(random: Random): String {
    val length = if (random.nextInt(8) == 0) {
      EventGraph.MAX_COALESCED_INSERT - 20 + random.nextInt(60)
    } else {
      1 + random.nextInt(4)
    }
    val text = StringBuilder()
    repeat(length) {
      text.append(ALPHABET[random.nextInt(ALPHABET.length)])
    }
    return text.toString()
  }

  companion object {
    private const val ROUNDS = 5000
    private const val ALPHABET = "abcdef\n"
  }
}
