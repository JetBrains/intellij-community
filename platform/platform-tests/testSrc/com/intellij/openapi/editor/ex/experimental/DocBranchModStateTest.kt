// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Tests [DocumentBranch.modState]. Each op folds into it, and a merge folds its ops into the mod state of
 * the receiver.
 */
class DocBranchModStateTest {

  @Test
  fun `a new branch has no modified lines`() {
    val modState = DocumentBranch.createBranch(TEXT, agent("a")).modState()
    assertEquals(0, modState.sequence())
    assertFalse(modState.isLineModified(0))
  }

  @Test
  fun `an insert keeps the stamp and marks its line`() {
    val base = DocumentBranch.createBranch(TEXT, agent("a"))
    val modState = base.applyOp(insertOp(4, "2")).modState()
    assertEquals(base.modState().stamp(), modState.stamp())
    assertEquals(1, modState.sequence())
    assertFalse(modState.isLineModified(0))
    assertTrue(modState.isLineModified(1))
    assertFalse(modState.isLineModified(2))
  }

  @Test
  fun `a replace counts its delete and its insert`() {
    val base = DocumentBranch.createBranch(TEXT, agent("a"))
    val replaced = base.applyOp(deleteOp(8, 5)).applyOp(insertOp(8, "THREE"))
    val modState = replaced.modState()
    assertEquals(2, modState.sequence())
    assertFalse(modState.isLineModified(1))
    assertTrue(modState.isLineModified(2))
  }

  @Test
  fun `a stamp op changes only the mod state`() {
    val base = DocumentBranch.createBranch(TEXT, agent("a"))
    val stamped = base.applyOp(modStampOp(42, true))
    assertEquals(42, stamped.modState().stamp())
    assertEquals(1, stamped.modState().sequence())
    assertSame(base.text(), stamped.text())
    assertSame(base.graph(), stamped.graph())
  }

  @Test
  fun `a clean lines op clears the marks and keeps the text`() {
    val edited = DocumentBranch.createBranch(TEXT, agent("a")).applyOp(insertOp(0, "1"))
    val clearAll = cleanLinesOp(0, Int.MAX_VALUE, IntArray(0))
    val cleared = edited.applyOp(clearAll)
    assertFalse(cleared.modState().isLineModified(0))
    assertEquals(edited.modState().sequence(), cleared.modState().sequence())
    assertSame(edited.text(), cleared.text())
    assertSame(edited.graph(), cleared.graph())
  }

  @Test
  fun `a fork shares the mod state and edits its own`() {
    val base = DocumentBranch.createBranch(TEXT, agent("a")).applyOp(insertOp(0, "1"))
    val fork = base.fork(agent("b"))
    assertSame(base.modState(), fork.modState())
    val forkEdited = fork.applyOp(insertOp(5, "2"))
    assertTrue(forkEdited.modState().isLineModified(1))
    assertFalse(base.modState().isLineModified(1))
  }

  @Test
  fun `each side of a merge keeps its own mod state`() {
    val base = DocumentBranch.createBranch(TEXT, agent("base"))
    val a = base.fork(agent("a"))
      .applyOp(insertOp(0, "1"))
      .applyOp(modStampOp(10, false))
    val b = base.fork(agent("b"))
      .applyOp(insertOp(8, "3"))
      .applyOp(modStampOp(20, false))
    val ab = a.mergeWithOps(b)
    val ba = b.mergeWithOps(a)
    assertEquals(ab.branch().text().string(), ba.branch().text().string())
    assertEquals(10, ab.branch().modState().stamp())
    assertEquals(20, ba.branch().modState().stamp())
    val merged = ab.branch().modState()
    assertEquals(a.modState().sequence() + ab.ops().size, merged.sequence())
    assertTrue(merged.isLineModified(0))
    assertFalse(merged.isLineModified(1))
    assertTrue(merged.isLineModified(2))
  }

  @Test
  fun `a fast-forward folds its ops into the mod state of the receiver`() {
    val base = DocumentBranch.createBranch(TEXT, agent("base"))
    val edited = base.fork(agent("a")).applyOp(insertOp(4, "2"))
    val forward = base.mergeWithOps(edited)
    val modState = forward.branch().modState()
    assertEquals(base.modState().stamp(), modState.stamp())
    assertEquals(forward.ops().size, modState.sequence())
    assertFalse(modState.isLineModified(0))
    assertTrue(modState.isLineModified(1))
    assertSame(modState, forward.branch().modState())
    assertSame(modState, forward.branch().fork(agent("b")).modState())
  }

  @Test
  fun `an op on a fast-forward folds into its mod state`() {
    val base = DocumentBranch.createBranch(TEXT, agent("base"))
    val edited = base.fork(agent("a")).applyOp(insertOp(4, "2"))
    val forward = base.merge(edited)
    val sameStamp = modStampOp(forward.modState().stamp(), false)
    assertSame(forward, forward.applyOp(sameStamp))
    val stamped = forward.applyOp(modStampOp(7, false))
    assertEquals(7, stamped.modState().stamp())
    val forwardEdited = forward.applyOp(insertOp(0, "1"))
    assertEquals(forward.modState().sequence() + 1, forwardEdited.modState().sequence())
    assertTrue(forwardEdited.modState().isLineModified(0))
  }

  @Test
  fun `a merged move folds as the same move applied locally`() {
    val base = DocumentBranch.createBranch(TEXT, agent("base"))
    // The first line moves after the second one.
    val moved = base.fork(agent("a")).moveText(0, 4, 8)
    val merge = base.mergeWithOps(moved)
    assertEquals(moveOps(TEXT, 0, 4, 8), merge.ops())
    val local = base.moveText(0, 4, 8)
    assertSameModState(local, merge.branch(), "a move")
  }

  @Test
  fun `a long chain of fast-forwards resolves its mod state`() {
    val base = DocumentBranch.createBranch(TEXT, agent("base"))
    var writer = base.fork(agent("writer"))
    var reader = base
    repeat(CHAIN_LENGTH) {
      writer = writer.applyOp(insertOp(0, "x"))
      reader = reader.merge(writer)
    }
    assertEquals(CHAIN_LENGTH, reader.modState().sequence())
  }

  @Test
  fun `a merge gives the mod state of its ops applied to the receiver`() {
    repeat(SEEDS) { seed ->
      val random = Random(seed.toLong())
      val base = DocumentBranch.createBranch(TEXT, agent("base"))
      val a = randomEdits(base.fork(agent("a")), random)
      val b = randomEdits(base.fork(agent("b")), random)
      assertMergeFoldsOps(a, b, "seed $seed, a concurrent merge")
      assertMergeFoldsOps(base, a, "seed $seed, a fast-forward")
      val forward = base.merge(a)
      assertMergeFoldsOps(forward, b, "seed $seed, a concurrent merge into a fast-forward")
    }
  }

  /**
   * Checks that the merge of [other] into [receiver] gives the mod state that the ops of the merge give
   * when they apply to [receiver] one by one.
   */
  private fun assertMergeFoldsOps(receiver: DocumentBranch, other: DocumentBranch, message: String) {
    val merge = receiver.mergeWithOps(other)
    var expected = receiver
    for (op in merge.ops()) {
      expected = expected.applyOp(op)
    }
    assertSameModState(expected, merge.branch(), message)
  }

  private fun assertSameModState(expected: DocumentBranch, actual: DocumentBranch, message: String) {
    val expectedModState = expected.modState()
    val actualModState = actual.modState()
    assertEquals(expectedModState.stamp(), actualModState.stamp(), message)
    assertEquals(expectedModState.sequence(), actualModState.sequence(), message)
    for (line in 0 until expected.text().lineCount()) {
      val expectedModified = expectedModState.isLineModified(line)
      assertEquals(expectedModified, actualModState.isLineModified(line)) { "$message, line $line" }
    }
  }

  private fun randomEdits(branch: DocumentBranch, random: Random): DocumentBranch {
    var edited = branch
    repeat(1 + random.nextInt(MAX_EDITS)) {
      edited = edited.applyOp(randomOp(edited.text(), random))
    }
    return edited
  }

  private fun randomOp(text: DocumentText, random: Random): DocumentOp.Text {
    val length = text.length()
    if (length == 0 || random.nextBoolean()) {
      val offset = random.nextInt(length + 1)
      val fragment = FRAGMENTS[random.nextInt(FRAGMENTS.size)]
      return insertOp(offset, fragment)
    }
    val offset = random.nextInt(length)
    val count = 1 + random.nextInt(minOf(3, length - offset))
    return deleteOp(offset, count)
  }

  private companion object {
    const val TEXT = "one\ntwo\nthree\n"
    const val SEEDS = 200
    const val MAX_EDITS = 6
    const val CHAIN_LENGTH = 20_000
    val FRAGMENTS = listOf("x", "yz", "\n", "a\nb")
  }
}
