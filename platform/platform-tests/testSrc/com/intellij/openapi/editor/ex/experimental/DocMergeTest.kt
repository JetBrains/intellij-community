// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tests [DocBranch.mergeWithOps] and the op stream of [DocMerge]: the ops of each merge outcome,
 * the joins that keep the stream short, and the rule that the ops fold the text of the receiver
 * into the merged text. [DocBranchGossipFuzzTest] checks that rule for every random merge.
 */
class DocMergeTest {

  @Test
  fun `a merge that brings nothing has no ops`() {
    val base = DocBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(insertOp(0, "1"))
    val b = base.fork(agent("b")).applyOp(insertOp(3, "2"))
    val merged = a.merge(b)
    for ((receiver, other) in listOf(base to base, a to base, merged to b, merged to a)) {
      val merge = receiver.mergeWithOps(other)
      assertSame(receiver, merge.branch())
      assertEquals(emptyList<DocumentOp.Text>(), merge.ops())
    }
  }

  @Test
  fun `a merge whose new units change no text has no ops`() {
    // Both sides delete the same character, so the delete of the other side finds it gone.
    val base = DocBranch.createBranch("abc", agent("base"))
    val a = base.fork(agent("a")).applyOp(deleteOp(1, 1))
    val b = base.fork(agent("b")).applyOp(deleteOp(1, 1))
    val merge = a.mergeWithOps(b)
    assertEquals(emptyList<DocumentOp.Text>(), merge.ops())
    assertSame(a.text(), merge.branch().text())
    assertEquals(a.graph().size() + 1, merge.branch().graph().size())
  }

  @Test
  fun `a fast-forward gives the edits of the descendant`() {
    val base = DocBranch.createBranch("abc", agent("a"))
    val descendant = base.fork(agent("b")).applyOp(insertOp(3, "def")).applyOp(deleteOp(0, 1))
    val merge = base.mergeWithOps(descendant)
    // The text needs no replay: it is the text of the descendant.
    assertSame(descendant.text(), merge.branch().text())
    assertEquals(listOf(DocumentOp.insertOp(3, "def"), DocumentOp.deleteOp(0, 1)), merge.ops())
    assertEquals("bcdef", base.text().afterOps(merge.ops()).string())
  }

  @Test
  fun `a fast-forward of an empty branch inserts the whole text`() {
    val empty = DocBranch.createBranch("", agent("a"))
    val typed = empty.fork(agent("b")).applyOp(insertOp(0, "xy")).applyOp(insertOp(2, "z"))
    assertEquals(listOf(DocumentOp.insertOp(0, "xyz")), empty.mergeWithOps(typed).ops())
    assertEquals(emptyList<DocumentOp.Text>(), typed.mergeWithOps(empty).ops())
  }

  @Test
  fun `concurrent inserts give one op to each side`() {
    val base = DocBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(insertOp(0, "1"))
    val b = base.fork(agent("b")).applyOp(insertOp(3, "2"))
    // Each side gets the edit of the other, at its place in the text of the receiver.
    assertEquals(listOf(DocumentOp.insertOp(4, "2")), a.mergeWithOps(b).ops())
    assertEquals(listOf(DocumentOp.insertOp(0, "1")), b.mergeWithOps(a).ops())
  }

  @Test
  fun `a concurrent paste arrives as one op`() {
    val base = DocBranch.createBranch("base\n", agent("base"))
    val left = base.fork(agent("aaa")).applyOp(insertOp(5, "L".repeat(PASTE)))
    val right = base.fork(agent("bbb")).applyOp(insertOp(5, "R".repeat(PASTE)))
    // The agent order puts the paste of "aaa" first.
    val intoLeft = left.mergeWithOps(right).ops().single() as DocumentOp.Insert
    assertEquals(5 + PASTE, intoLeft.offset())
    assertEquals("R".repeat(PASTE), intoLeft.fragment().toString())
    val intoRight = right.mergeWithOps(left).ops().single() as DocumentOp.Insert
    assertEquals(5, intoRight.offset())
    assertEquals("L".repeat(PASTE), intoRight.fragment().toString())
  }

  @Test
  fun `text that the other side typed and deleted brings no op`() {
    // The other side typed "xyz" and took it back with three backspaces.
    val base = DocBranch.createBranch("ab", agent("base"))
    val a = base.fork(agent("a")).applyOp(insertOp(0, "1"))
    var b = base.fork(agent("b")).applyOp(insertOp(2, "xyz"))
    for (offset in 4 downTo 2) {
      b = b.applyOp(deleteOp(offset, 1))
    }
    val merge = a.mergeWithOps(b)
    assertEquals(emptyList<DocumentOp.Text>(), merge.ops())
    assertEquals("1ab", merge.branch().string())
    assertEquals(emptyList<DocumentOp.Text>(), base.mergeWithOps(b).ops())
  }

  @Test
  fun `backspaces over old text arrive as one delete`() {
    val base = DocBranch.createBranch("abcdef", agent("base"))
    val a = base.fork(agent("a")).applyOp(insertOp(6, "!"))
    var b = base.fork(agent("b"))
    for (offset in 3 downTo 1) {
      b = b.applyOp(deleteOp(offset, 1))
    }
    assertEquals(listOf(DocumentOp.deleteOp(1, 3)), a.mergeWithOps(b).ops())
    assertEquals(listOf(DocumentOp.deleteOp(1, 3)), base.mergeWithOps(b).ops())
  }

  @Test
  fun `a surrogate pair arrives whole in one op`() {
    val face = "😀"
    val base = DocBranch.createBranch("ab", agent("base"))
    val a = base.fork(agent("a")).applyOp(insertOp(1, face))
    val b = base.fork(agent("b")).applyOp(insertOp(1, "x"))
    val op = b.mergeWithOps(a).ops().single() as DocumentOp.Insert
    assertEquals(1, op.offset())
    assertEquals(face, op.fragment().toString())
    assertEquals("a${face}xb", b.merge(a).string())
  }

  @Test
  fun `branches with no common event fold into their merged text`() {
    val base = DocBranch.createBranch("", agent("base"))
    val a = base.fork(agent("a")).applyOp(insertOp(0, "xy"))
    val b = base.fork(agent("b")).applyOp(insertOp(0, "pq"))
    for ((receiver, other) in listOf(a to b, b to a)) {
      val merge = receiver.mergeWithOps(other)
      assertEquals(merge.branch().string(), receiver.text().afterOps(merge.ops()).string())
    }
  }

  @Test
  fun `a merge that changes no text can still have ops`() {
    // The other side typed at both ends and took both back, so the text ends where it started.
    // The two inserts do not meet, so no join cancels them.
    val base = DocBranch.createBranch("hello", agent("base"))
    val b = base.fork(agent("b"))
      .applyOp(insertOp(0, "X")).applyOp(insertOp(6, "Y"))
      .applyOp(deleteOp(0, 1)).applyOp(deleteOp(5, 1))
    val forward = base.mergeWithOps(b)
    assertEquals("hello", forward.branch().string())
    assertEquals(listOf(DocumentOp.insertOp(0, "X"), DocumentOp.insertOp(6, "Y"), DocumentOp.deleteOp(0, 1), DocumentOp.deleteOp(5, 1)), forward.ops())
    val a = base.fork(agent("a")).applyOp(insertOp(5, "!"))
    val concurrent = a.mergeWithOps(b)
    assertEquals(a.string(), concurrent.branch().string())
    assertEquals(4, concurrent.ops().size)
    assertEquals(a.string(), a.text().afterOps(concurrent.ops()).string())
  }

  /**
   * One agent minted (b, 1) twice, as "M" and as "N". The id check samples only the ends of the
   * shared range, (b, 0) and (b, 2), so the merge misses the clash and fast-forwards to a text
   * that its own graph does not replay to. The ops must then fail, and not hand an editor a text
   * that differs from the branch.
   */
  @Test
  fun `the ops of a fast-forward fail when a missed clash breaks the fold`() {
    val base = DocBranch.createBranch("abc", agent("a"))
    val x = base.fork(agent("b")).applyOp(insertOp(0, "X")).applyOp(insertOp(4, "M")).applyOp(insertOp(0, "Z"))
    val y = base.fork(agent("b")).applyOp(insertOp(0, "X")).applyOp(insertOp(1, "N")).applyOp(insertOp(0, "Z"))
    val merge = x.mergeWithOps(y.fork(agent("c")).applyOp(insertOp(0, "W")))
    assertEquals("WZXNabc", merge.branch().string())
    val failure = assertThrows(IllegalArgumentException::class.java) { merge.ops() }
    assertTrue(failure.message.orEmpty().contains("another text than the merged text"), failure.message)
  }

  @Test
  fun `a merge returns the branch of mergeWithOps`() {
    val base = DocBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(insertOp(0, "1"))
    val b = base.fork(agent("b")).applyOp(deleteOp(1, 1))
    for ((receiver, other) in listOf(a to b, b to a, base to b)) {
      val merged = receiver.merge(other)
      val withOps = receiver.mergeWithOps(other).branch()
      assertEquals(merged.string(), withOps.string())
      assertEquals(merged.agent(), withOps.agent())
      assertEquals(merged.graph().size(), withOps.graph().size())
      assertEquals(merged.graph().version(), withOps.graph().version())
    }
  }

  @Test
  fun `a clash fails the same way with ops`() {
    val base = DocBranch.createBranch("abc", agent("a"))
    val x = base.applyOp(insertOp(0, "X"))
    val y = base.applyOp(insertOp(0, "Y"))
    assertThrows(EventIdClashException::class.java) { x.mergeWithOps(y) }
  }

  @Test
  fun `the ops of every outcome cannot change`() {
    val base = DocBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(insertOp(0, "1"))
    val b = base.fork(agent("b")).applyOp(insertOp(3, "2"))
    // A partial replay, a fast-forward, and a merge that brings nothing.
    for (merge in listOf(a.mergeWithOps(b), base.mergeWithOps(b), a.mergeWithOps(base))) {
      @Suppress("UNCHECKED_CAST")
      val ops = merge.ops() as MutableList<DocumentOp.Text>
      assertThrows(UnsupportedOperationException::class.java) { ops.add(DocumentOp.insertOp(0, "x")) }
      assertSame(merge.ops(), merge.ops())
    }
  }

  /**
   * One fast-forward shared by many threads. Its ops are built on the first call, and every thread
   * must get the same list, which folds to the merged text.
   */
  @Test
  fun `threads that read the ops of one fast-forward agree`() {
    val random = Random(20261001)
    val base = DocBranch.createBranch("0123456789".repeat(20), agent("base"))
    var descendant = base.fork(agent("b"))
    repeat(200) {
      val length = descendant.length()
      descendant = if (length > 0 && random.nextBoolean()) {
        val offset = random.nextInt(length)
        descendant.applyOp(deleteOp(offset, 1 + random.nextInt(minOf(3, length - offset))))
      } else {
        descendant.applyOp(insertOp(random.nextInt(length + 1), "xyz".substring(random.nextInt(3))))
      }
    }
    val pool = Executors.newFixedThreadPool(THREADS)
    try {
      repeat(ROUNDS) {
        val merge = base.mergeWithOps(descendant)
        val start = CountDownLatch(1)
        val results = (0 until THREADS).map {
          pool.submit<List<DocumentOp.Text>> {
            start.await()
            val ops = merge.ops()
            assertEquals(descendant.string(), base.text().afterOps(ops).string())
            ops
          }
        }
        start.countDown()
        val lists = results.map { it.get(30, TimeUnit.SECONDS) }
        for (list in lists) {
          assertSame(lists[0], list)
        }
        assertTrue(lists[0].isNotEmpty())
      }
    } finally {
      pool.shutdownNow()
    }
  }

  private companion object {
    const val PASTE = 20_000
    const val THREADS = 8
    const val ROUNDS = 50
  }
}
