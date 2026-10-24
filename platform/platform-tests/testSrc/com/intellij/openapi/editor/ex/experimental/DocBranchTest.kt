// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.fail

class DocBranchTest {

  @Test
  fun `local edits match a plain DocumentText`() {
    var plain = DocumentText.createText("fun main() {\n  println()\n}\n")
    var branch: DocumentBranch = DocumentBranch.createBranch(plain.string(), agent("user"))
    val ops = listOf(
      insertOp(13, "  val x = 1\n"),
      deleteOp(0, 4),
      insertOp(0, "private fun "),
      deleteOp(20, 3),
    )
    for (op in ops) {
      plain = plain.applyOp(op)
      branch = branch.applyOp(op)
      assertSameText(plain, branch.text())
    }
    val tail = insertOp(plain.length(), "// end")
    assertSameText(plain.applyOp(tail), branch.applyOp(tail).text())
  }

  @Test
  fun `concurrent inserts converge and order by agent`() {
    val base = DocumentBranch.createBranch("", agent("a"))
    val a = base.applyOp(insertOp(0, "X"))
    val b = base.fork(agent("b")).applyOp(insertOp(0, "Y"))
    val ab = a.merge(b)
    val ba = b.merge(a)
    // Fugue breaks the tie by the agent order, so both replicas see the same text.
    assertEquals("XY", ab.string())
    assertEquals("XY", ba.string())
  }

  @Test
  fun `concurrent runs do not interleave`() {
    val base = DocumentBranch.createBranch("()", agent("a"))
    val a = base.applyOp(insertOp(1, "111"))
    val b = base.fork(agent("b")).applyOp(insertOp(1, "222"))
    val merged = a.merge(b)
    assertEquals("(111222)", merged.string())
    assertEquals(merged.string(), b.merge(a).string())
  }

  @Test
  fun `an insert into a concurrently deleted range survives`() {
    val base = DocumentBranch.createBranch("abcdef", agent("a"))
    val a = base.applyOp(deleteOp(1, 3)) // deletes "bcd" -> "aef"
    val b = base.fork(agent("b")).applyOp(insertOp(3, "X")) // -> "abcXdef"
    val ab = a.merge(b)
    val ba = b.merge(a)
    assertEquals("aXef", ab.string())
    assertEquals("aXef", ba.string())
  }

  @Test
  fun `a concurrent delete of one char deletes it once`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(deleteOp(1, 1))
    val b = base.fork(agent("b")).applyOp(deleteOp(1, 1))
    assertEquals("ac", a.merge(b).string())
    assertEquals("ac", b.merge(a).string())
  }

  @Test
  fun `a merge with an ancestor changes nothing`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(insertOp(3, "def"))
    assertSame(a, a.merge(base))
  }

  @Test
  fun `a merge with a descendant fast-forwards`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val b = base.fork(agent("b")).applyOp(insertOp(3, "def")).applyOp(deleteOp(0, 1))
    val merged = base.merge(b)
    assertEquals("bcdef", merged.string())
    // A fast-forward takes the text of the descendant as it is, so no replay ran.
    assertSame(b.text(), merged.text())
  }

  @Test
  fun `a repeated merge changes nothing`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(insertOp(0, "1"))
    val b = base.fork(agent("b")).applyOp(insertOp(3, "2"))
    val merged = a.merge(b)
    assertSame(merged, merged.merge(b))
    assertSame(merged, merged.merge(a))
  }

  @Test
  fun `edits continue after a merge`() {
    val base = DocumentBranch.createBranch("start\n", agent("a"))
    val a = base.applyOp(insertOp(6, "from a\n"))
    val b = base.fork(agent("b")).applyOp(insertOp(6, "from b\n"))
    val merged = a.merge(b)
    val edited = merged.applyOp(insertOp(merged.length(), "end\n"))
    val c = merged.fork(agent("c")).applyOp(insertOp(0, "top\n"))
    val again = edited.merge(c)
    assertEquals(again.string(), c.merge(edited).string())
    assertEquals("top\nstart\nfrom a\nfrom b\nend\n", again.string())
  }

  @Test
  fun `the merged text matches a replay of the merged graph`() {
    val base = DocumentBranch.createBranch("one\ntwo\n", agent("a"))
    val a = base.applyOp(insertOp(4, "1.5\n"))
    val b = base.fork(agent("b")).applyOp(deleteOp(0, 4))
    val merged = a.merge(b)
    assertEquals(merged.graph().replay().string(), merged.string())
  }

  @Test
  fun `the line structure after a merge matches a fresh DocumentText`() {
    val base = DocumentBranch.createBranch("a\nb\nc\n", agent("a"))
    val a = base.applyOp(insertOp(2, "a2\n"))
    val b = base.fork(agent("b")).applyOp(deleteOp(4, 2)).applyOp(insertOp(0, "top\n"))
    val merged = a.merge(b)
    assertSameText(DocumentText.createText(merged.string()), merged.text())
  }

  @Test
  fun `an empty op keeps the instance`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    assertSame(base, base.applyOp(insertOp(1, "")))
    assertSame(base, base.applyOp(deleteOp(1, 0)))
  }

  @Test
  fun `a metadata op that changes nothing keeps the instance`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val sameStamp = modStampOp(base.modState().stamp(), false)
    // A new branch has no modified lines to clear.
    val noModifiedLines = cleanLinesOp(0, 1, IntArray(0))
    assertSame(base, base.applyOp(sameStamp))
    assertSame(base, base.applyOp(noModifiedLines))
  }

  @Test
  fun `a new instance keeps the state and the agent`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    // A fast-forward defers its mod state. The new instance must give the same one.
    val forward = base.merge(base.fork(agent("b")).applyOp(insertOp(1, "x")))
    val copy = forward.newInstance()
    assertNotSame(forward, copy)
    assertSame(forward.text(), copy.text())
    assertSame(forward.modState(), copy.modState())
    assertSame(forward.graph(), copy.graph())
    assertEquals(forward.agent(), copy.agent())
    assertEquals(forward.version(), copy.version())
  }

  @Test
  fun `dumpState lists the line ranges of the text`() {
    val branch = DocumentBranch.createBranch("ab", agent("a")).applyOp(insertOp(2, "\ncd"))
    assertEquals("intervals:\n0: 0-2, 1: 3-5", branch.dumpState())
  }

  @Test
  fun `a merge of two merged branches converges`() {
    val base = DocumentBranch.createBranch("abc\n", agent("a"))
    val a = base.applyOp(insertOp(0, "1"))
    val b = base.fork(agent("b")).applyOp(insertOp(4, "2"))
    val c = base.fork(agent("c")).applyOp(deleteOp(1, 1))
    // Both sides of the final merge are merge results with multi-head versions.
    val left = a.merge(b)
    val right = b.merge(c)
    val forward = left.merge(right)
    val backward = right.merge(left)
    assertEquals("1ac\n2", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
    assertSameText(DocumentText.createText(forward.string()), forward.text())
  }

  @Test
  fun `a concurrent edit survives a full deletion`() {
    val base = DocumentBranch.createBranch("abcdef", agent("a"))
    val wipe = base.applyOp(deleteOp(0, 6))
    val edit = base.fork(agent("b")).applyOp(insertOp(3, "X"))
    assertEquals("X", wipe.merge(edit).string())
    assertEquals("X", edit.merge(wipe).string())
  }

  @Test
  fun `alternating pulls converge over many rounds`() {
    // A staircase history: the common ancestor of every merge climbs round by round.
    var x = DocumentBranch.createBranch("seed\n", agent("x"))
    var y = x.fork(agent("y"))
    for (round in 0 until 12) {
      x = x.applyOp(insertOp(0, "x$round "))
      y = y.applyOp(insertOp(y.text().length(), " y$round"))
      if (round % 2 == 0) {
        x = x.merge(y)
      } else {
        y = y.merge(x)
      }
      val forward = x.merge(y)
      val backward = y.merge(x)
      assertEquals(forward.string(), backward.string()) { "round $round" }
      assertEquals(forward.graph().replay().string(), forward.string()) { "round $round" }
    }
    val merged = x.merge(y)
    assertSameText(DocumentText.createText(merged.string()), merged.text())
    for (round in 0 until 12) {
      // Every marker is a word of its own, so "x1" cannot pass on "x10" or "x11".
      val words = merged.string().split(' ', '\n')
      assertEquals(1, words.count { it == "x$round" }) { "The edit x$round is lost or doubled" }
      assertEquals(1, words.count { it == "y$round" }) { "The edit y$round is lost or doubled" }
    }
  }

  @Test
  fun `a merge applies pastes and deletions as batches`() {
    val base = DocumentBranch.createBranch("line1\nline2\nline3\n", agent("a"))
    val a = base.applyOp(insertOp(0, "top\n"))
    // The other branch mixes a multi-char paste, a range deletion, and another paste,
    // so the merge sink flushes at every kind and position boundary.
    val b = base.fork(agent("b"))
      .applyOp(insertOp(6, "pasted block\n"))
      .applyOp(deleteOp(0, 6))
      .applyOp(insertOp(0, "L1\n"))
    val merge = a.mergeWithOps(b)
    val forward = merge.branch()
    val backward = b.merge(a)
    // The reference gives this text.
    assertEquals("top\nL1\npasted block\nline2\nline3\n", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
    // The paste and the range delete each arrive as one op, and not one per character.
    val ops = listOf(
      DocumentOp.insertOp(10, "pasted block\n"),
      DocumentOp.deleteOp(4, 6),
      DocumentOp.insertOp(4, "L1\n"),
    )
    assertEquals(ops, merge.ops())
    assertSameText(DocumentText.createText(forward.string()), forward.text())
  }

  @Test
  fun `a fork from a stale value mints fresh seqs`() {
    val base = DocumentBranch.createBranch("xy", agent("a"))
    // The abandoned fork makes two runs, so its first run closes into the fork's own trees. The
    // base shares their older nodes and must not see it.
    val abandoned = base.fork(agent("b")).applyOp(insertOp(0, "A")).applyOp(insertOp(0, "Z"))
    // A fork of the same agent from the stale base must not see those units.
    val fork = base.fork(agent("b")).applyOp(insertOp(2, "B"))
    assertEquals("xyB", fork.string())
    assertEquals("xyB", base.merge(fork).string())
    // The abandoned branch stays intact; it is never merged with its twin.
    assertEquals("ZAxy", abandoned.string())
  }

  @Test
  fun `a fragment op makes one run`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val edited = base
      .applyOp(insertOp(1, "xyz"))
      .applyOp(deleteOp(0, 2))
    assertEquals(5, edited.graph().size())
    assertEquals(2, edited.graph().runCount())
  }

  @Test
  fun `a resumed agent continues its seqs after multi-unit runs`() {
    val base = DocumentBranch.createBranch("0123", agent("a"))
    val b1 = base.fork(agent("b")).applyOp(insertOp(4, "bbb")).applyOp(deleteOp(0, 2))
    val merged = base.merge(b1) // the agent "b" owns five unit ids now, in two runs
    val resumed = merged.fork(agent("b")).applyOp(insertOp(0, "RR"))
    val sibling = merged.fork(agent("c")).applyOp(insertOp(0, "CC"))
    val forward = resumed.merge(sibling)
    val backward = sibling.merge(resumed)
    // A seq collision would make the merge drop "RR" as an already known run. The reference gives
    // this text: "b" sorts before "c", so "RR" comes first.
    assertEquals("RRCC23bbb", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
  }

  @Test
  fun `a large document costs no event and fast-forwards without a replay`() {
    val size = 100_000
    val text = buildString {
      repeat(size) { append('x') }
    }
    val base = DocumentBranch.createBranch(text, agent("a"))
    assertEquals(0, base.graph().size())
    assertEquals(0, base.graph().runCount())
    val edited = base.fork(agent("b")).applyOp(insertOp(size, "end"))
    // A fast-forward adopts the text of the descendant as it is, so no replay ran.
    val merged = base.merge(edited)
    assertSame(edited.text(), merged.text())
    assertEquals(1, merged.graph().runCount())
  }

  @Test
  fun `the base text of a new branch is the text of the branch`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    assertTrue(base.version().isRoot())
    assertEquals("abc", base.graph().replay().string())
    val edited = base.applyOp(insertOp(1, "x"))
    assertEquals("axbc", edited.graph().replay().string())
    assertEquals("abc", edited.graph().replay(Version.root()).string())
    // A fork shares the base text, so the merge replays its edit over the same text.
    val forked = base.fork(agent("b")).applyOp(deleteOp(2, 1))
    val merged = edited.merge(forked)
    assertEquals("axb", merged.string())
    assertEquals("axb", merged.graph().replay().string())
  }

  @Test
  fun `a merge with an unedited fork keeps the instance`() {
    val base = DocumentBranch.createBranch("abc", agent("a")).applyOp(insertOp(0, "x"))
    val fork = base.fork(agent("b"))
    assertSame(base, base.merge(fork))
  }

  @Test
  fun `concurrent inserts at the opposite ends converge`() {
    val base = DocumentBranch.createBranch("mid", agent("a"))
    val left = base.applyOp(insertOp(0, "L"))
    val right = base.fork(agent("b")).applyOp(insertOp(3, "R"))
    assertEquals("LmidR", left.merge(right).string())
    assertEquals("LmidR", right.merge(left).string())
  }

  /**
   * One delete run removes "a", "X" and "b", and "X" is a run of its own between the other two.
   * So the delete units continue while the units they deleted do not. The merge retreats the run,
   * and it must undelete exactly those three characters.
   */
  @Test
  fun `a delete run across interleaved runs retreats its own characters`() {
    val base = DocumentBranch.createBranch("abc", agent("base"))
    val edited = base.fork(agent("a")).applyOp(insertOp(1, "X")).applyOp(deleteOp(0, 3))
    val concurrent = base.fork(agent("b")).applyOp(insertOp(3, "Y"))
    assertEquals("c", edited.string())
    assertEquals("cY", edited.merge(concurrent).string())
    assertEquals("cY", concurrent.merge(edited).string())
  }

  @Test
  fun `concurrent overlapping deletes erase the union of the ranges`() {
    val base = DocumentBranch.createBranch("abcdef", agent("a"))
    val a = base.applyOp(deleteOp(1, 2)) // deletes "bc" -> "adef"
    val b = base.fork(agent("b")).applyOp(deleteOp(2, 2)) // deletes "cd" -> "abef"
    assertEquals("aef", a.merge(b).string())
    assertEquals("aef", b.merge(a).string())
  }

  @Test
  fun `concurrent deletes that cover the text erase all of it`() {
    val base = DocumentBranch.createBranch("abcdef", agent("a"))
    val a = base.applyOp(deleteOp(0, 4)) // -> "ef"
    val b = base.fork(agent("b")).applyOp(deleteOp(2, 4)) // -> "ab"
    assertEquals("", a.merge(b).string())
    assertEquals("", b.merge(a).string())
  }

  @Test
  fun `a criss-cross merge converges`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a1 = base.applyOp(insertOp(0, "1"))
    val b1 = base.fork(agent("b")).applyOp(insertOp(3, "2"))
    // Both sides merge each other, then edit again, then merge again.
    val a2 = a1.merge(b1).applyOp(insertOp(0, "3"))
    val b2 = b1.merge(a1).applyOp(insertOp(0, "4"))
    val forward = a2.merge(b2)
    val backward = b2.merge(a2)
    // The reference gives this text.
    assertEquals("341abc2", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
    assertSameText(DocumentText.createText(forward.string()), forward.text())
  }

  @Test
  fun `an incremental sync equals a fresh merge`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a1 = base.applyOp(insertOp(3, "1"))
    var b1 = base.fork(agent("b")).applyOp(insertOp(0, "2"))
    val first = a1.merge(b1)
    b1 = b1.applyOp(insertOp(0, "3"))
    val incremental = first.merge(b1)
    val fresh = a1.merge(b1)
    assertEquals(fresh.string(), incremental.string())
    assertEquals(incremental.graph().replay().string(), incremental.string())
  }

  @Test
  fun `an edit based on a pre-merge state merges cleanly`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a1 = base.applyOp(deleteOp(1, 1)) // -> "ac"
    val b1 = base.fork(agent("b")).applyOp(insertOp(2, "Z")) // -> "abZc"
    val merged = a1.merge(b1) // -> "aZc"
    // The next edit descends from a1, not from the merge.
    val a2 = a1.applyOp(insertOp(1, "W")) // -> "aWc"
    // The replay must advance over a1's delete again; this is the advance-of-a-delete path.
    val forward = merged.merge(a2)
    val backward = a2.merge(merged)
    assertEquals("aWZc", forward.string())
    assertEquals("aWZc", backward.string())
  }

  @Test
  fun `three replicas converge in every merge order`() {
    val base = DocumentBranch.createBranch("base\n", agent("m"))
    val replicas = listOf(
      base.fork(agent("a")).applyOp(insertOp(0, "aa")),
      base.fork(agent("b")).applyOp(insertOp(5, "bb")),
      base.fork(agent("c")).applyOp(deleteOp(0, 2)),
    )
    val orders = listOf(
      listOf(0, 1, 2), listOf(0, 2, 1),
      listOf(1, 0, 2), listOf(1, 2, 0),
      listOf(2, 0, 1), listOf(2, 1, 0),
    )
    val texts = HashSet<String>()
    for (order in orders) {
      val merged = order.map { replicas[it] }.reduce { acc, replica -> acc.merge(replica) }
      texts.add(merged.string())
    }
    // Every order gives the text of the reference.
    assertEquals(setOf("aase\nbb"), texts)
  }

  @Test
  fun `surrogate pairs survive a concurrent merge`() {
    val base = DocumentBranch.createBranch("ab", agent("a"))
    val rocket = base.applyOp(insertOp(1, "🚀")) // 🚀
    val smile = base.fork(agent("b")).applyOp(insertOp(1, "🙂")) // 🙂
    val forward = rocket.merge(smile)
    val backward = smile.merge(rocket)
    // The runs do not interleave, so both pairs stay intact.
    assertEquals("a🚀🙂b", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(4, forward.string().codePointCount(0, forward.length()))
  }

  @Test
  fun `branches from two createBranch calls do not merge`() {
    val first = DocumentBranch.createBranch("ab", agent("a")).applyOp(insertOp(2, "x"))
    val second = DocumentBranch.createBranch("ab", agent("b")).applyOp(insertOp(2, "x"))
    for ((receiver, other) in listOf(first to second, second to first)) {
      assertThrows(LineageMismatchException::class.java) {
        receiver.merge(other)
      }
      assertThrows(LineageMismatchException::class.java) {
        receiver.mergeWithOps(other)
      }
    }
    // The failed merges changed nothing.
    assertEquals("abx", first.string())
    assertEquals("abxy", first.applyOp(insertOp(3, "y")).string())
  }

  @Test
  fun `CRLF separators merge and match a fresh DocumentText`() {
    val base = DocumentBranch.createBranch("a\r\nb", agent("a"))
    val a = base.applyOp(insertOp(1, "X")) // -> "aX\r\nb"
    val b = base.fork(agent("b")).applyOp(deleteOp(1, 1)) // deletes '\r' -> "a\nb"
    val forward = a.merge(b)
    assertEquals("aX\nb", forward.string())
    assertEquals(forward.string(), b.merge(a).string())
    assertEquals(2, forward.text().lineCount())
    assertEquals(1, forward.text().lineSeparatorLength(0))
    assertSameText(DocumentText.createText("aX\nb"), forward.text())
  }

  @Test
  fun `an invalid op throws and keeps the branch usable`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    assertThrows(IndexOutOfBoundsException::class.java) { base.applyOp(insertOp(4, "X")) }
    assertThrows(IndexOutOfBoundsException::class.java) { base.applyOp(insertOp(-1, "X")) }
    assertThrows(IndexOutOfBoundsException::class.java) { base.applyOp(deleteOp(2, 5)) }
    // The failed ops left no events behind.
    assertEquals(0, base.graph().size())
    assertEquals("abcX", base.applyOp(insertOp(3, "X")).string())
  }

  @Test
  fun `divergent edits on one instance stay independent`() {
    val base = DocumentBranch.createBranch("ab", agent("a"))
    val first = base.applyOp(insertOp(0, "X"))
    val second = base.applyOp(insertOp(2, "Y"))
    // Both values are usable, and each one appends to its own trees.
    // They must not merge with each other: one agent edited both, which the contract forbids.
    assertEquals("ab", base.string())
    assertEquals("Xab", first.string())
    assertEquals("abY", second.string())
    assertEquals(0, base.graph().size())
    assertEquals(1, first.graph().size())
    assertEquals(1, second.graph().size())
  }

  @Test
  fun `a fork that reuses an agent stays consistent after merges`() {
    val base = DocumentBranch.createBranch("ab", agent("a"))
    val b1 = base.fork(agent("b")).applyOp(insertOp(2, "1"))
    val merged = base.merge(b1)
    // The agent "b" resumes on a fresh fork; its seq numbers must continue, not restart.
    val resumed = merged.fork(agent("b")).applyOp(insertOp(0, "2"))
    val other = merged.fork(agent("c")).applyOp(insertOp(0, "3"))
    val forward = resumed.merge(other)
    val backward = other.merge(resumed)
    // Both inserts land at 0, and "b" sorts before "c".
    assertEquals("23ab1", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
  }

  @Test
  fun `a self merge keeps the instance`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val edited = base.applyOp(insertOp(3, "d"))
    assertSame(edited, edited.merge(edited))
  }

  @Test
  fun `a second round over two parentless runs converges`() {
    // Two forks of an empty branch each type at the root. The joined graph holds two
    // parentless runs, so its version has two heads. The next round merges above that
    // two-head version.
    val base = DocumentBranch.createBranch("", agent("base"))
    val first = base.fork(agent("a")).applyOp(insertOp(0, "aaa\n"))
    val second = base.fork(agent("b")).applyOp(insertOp(0, "bbb\n"))
    val joined = first.merge(second)
    assertEquals("aaa\nbbb\n", joined.string())
    val left = joined.applyOp(insertOp(0, "L"))
    // The delete crosses the seam between the two parentless runs.
    val right = joined.fork(agent("c")).applyOp(deleteOp(2, 4))
    val forward = left.merge(right)
    val backward = right.merge(left)
    assertEquals("Laab\n", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
    assertSameText(DocumentText.createText(forward.string()), forward.text())
  }

  @Test
  fun `a concurrent run beside a long run keeps both runs whole`() {
    // The long run makes the replay walk the implicit parent chain inside one run,
    // and the delete run removes units from the middle of it.
    val base = DocumentBranch.createBranch("()", agent("a"))
    val a = base
      .applyOp(insertOp(1, "0123456789"))
      .applyOp(deleteOp(5, 3)) // removes "456" -> "(0123789)"
    val b = base.fork(agent("b")).applyOp(insertOp(1, "ABC"))
    val forward = a.merge(b)
    val backward = b.merge(a)
    assertEquals("(0123789ABC)", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
  }

  @Test
  fun `a concurrent insert survives a delete run over the whole region`() {
    // The delete run consumes six placeholder units, so the merge splits the single placeholder
    // span, and the delete takes the six units as one piece.
    val base = DocumentBranch.createBranch("abcdefghij", agent("a"))
    val a = base.applyOp(deleteOp(2, 6)) // removes "cdefgh" -> "abij"
    val b = base.fork(agent("b"))
      .applyOp(insertOp(5, "X")) // inside the deleted range
      .applyOp(insertOp(0, "Y")) // before it
    val forward = a.merge(b)
    val backward = b.merge(a)
    assertEquals("YabXij", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
  }

  @Test
  fun `an insert at the end of an emptied document keeps its place`() {
    // The placeholder span is longer than the document at the common ancestor, so the
    // trailing units sit after every reachable position. The insert lands at the end of
    // the ancestor document, which is exactly the boundary of those trailing units.
    val base = DocumentBranch.createBranch("abcdefgh", agent("a"))
    val wipe = base.applyOp(deleteOp(0, 8))
    assertEquals("", wipe.string())
    val tail = base.fork(agent("b")).applyOp(insertOp(8, "TAIL"))
    val forward = wipe.merge(tail)
    val backward = tail.merge(wipe)
    assertEquals("TAIL", forward.string())
    assertEquals("TAIL", backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
  }

  @Test
  fun `three concurrent runs at one anchor converge in every order`() {
    // Every run has the same left origin and the same right parent, so the tie-break
    // by agent decides the whole order. This is the case the integrate scan resolves.
    val base = DocumentBranch.createBranch("<>", agent("m"))
    val replicas = listOf("a", "b", "c").map { name ->
      base.fork(agent(name)).applyOp(insertOp(1, name.repeat(3)))
    }
    val orders = listOf(
      listOf(0, 1, 2), listOf(0, 2, 1),
      listOf(1, 0, 2), listOf(1, 2, 0),
      listOf(2, 0, 1), listOf(2, 1, 0),
    )
    for (order in orders) {
      val merged = order.map { replicas[it] }.reduce { acc, replica -> acc.merge(replica) }
      assertEquals("<aaabbbccc>", merged.string()) { "the order $order" }
      assertEquals(merged.graph().replay().string(), merged.string()) { "the order $order" }
    }
  }

  @Test
  fun `an edit based on a pre-merge delete run merges cleanly`() {
    val base = DocumentBranch.createBranch("abcdef", agent("a"))
    val a1 = base.applyOp(deleteOp(1, 3)) // -> "aef"
    val b1 = base.fork(agent("b")).applyOp(insertOp(3, "Z")) // -> "abcZdef"
    val merged = a1.merge(b1) // -> "aZef"
    assertEquals("aZef", merged.string())
    // The next edit descends from a1, so the replay must advance over the delete run.
    val a2 = a1.applyOp(insertOp(1, "W")) // -> "aWef"
    val forward = merged.merge(a2)
    val backward = a2.merge(merged)
    assertEquals("aWZef", forward.string())
    assertEquals("aWZef", backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
  }

  @Test
  fun `a merge of two branches that share an event id fails loudly`() {
    // One agent edited the same value twice, which the contract forbids: both results
    // hold the id (a, 3). Before the merge check, the merge silently kept one edit and
    // dropped the other, and the two directions returned different texts.
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val sameSpot = base.applyOp(insertOp(0, "X"))
    for (clash in listOf(insertOp(0, "Y"), insertOp(2, "Y"), insertOp(0, "YY"), deleteOp(0, 1))) {
      val other = base.applyOp(clash)
      assertThrows(EventIdClashException::class.java, { sameSpot.merge(other) }, "$clash")
      assertThrows(EventIdClashException::class.java, { other.merge(sameSpot) }, "$clash")
    }
    // The same id with the same operation is a normal re-merge, not a clash.
    val twin = base.applyOp(insertOp(0, "X"))
    assertSame(sameSpot, sameSpot.merge(twin))
  }

  @Test
  fun `a concurrent delete inside a long run splits the span`() {
    // One paste op makes one run, and the replay covers it with one span. The other
    // branch deletes the middle of that run, so the merge must split the span in three
    // and retreat only the middle part.
    val base = DocumentBranch.createBranch("[]", agent("a"))
    val a = base.applyOp(insertOp(1, "0123456789"))
    val b = base.fork(agent("b")).merge(a).applyOp(deleteOp(4, 4)) // removes "3456"
    val a2 = a.applyOp(insertOp(6, "X")) // inside the range that b deletes
    val forward = b.merge(a2)
    val backward = a2.merge(b)
    assertEquals("[012X789]", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.graph().replay().string(), forward.string())
  }

  @Test
  fun `a replay at a merged past version returns that merged text`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a1 = base.applyOp(insertOp(3, "1"))
    val b1 = base.fork(agent("b")).applyOp(insertOp(0, "2"))
    val merged = a1.merge(b1)
    // The version of a merge has two heads; a replay at it must select that event set.
    val mergedVersion = merged.graph().version()
    val later = merged.applyOp(insertOp(0, "Z")).applyOp(deleteOp(1, 2))
    assertEquals(merged.string(), later.graph().replay(mergedVersion).string())
    assertEquals(later.string(), later.graph().replay().string())
  }

  @Test
  fun `the version of a merged branch replays its text in the merge`() {
    // A memory-disk merge: the user branch merges the disk branch and keeps both versions.
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val user = base.applyOp(insertOp(0, "U"))
    val disk = base.fork(agent("disk")).applyOp(insertOp(3, "D"))
    val merged = user.merge(disk).applyOp(insertOp(0, "Z"))
    assertEquals("ZUabcD", merged.string())
    assertEquals("abcD", merged.graph().replay(disk.version()).string())
    assertEquals("Uabc", merged.graph().replay(user.version()).string())
    assertEquals(disk.graph().version(), disk.version())
  }

  @Test
  fun `the version of a branch follows a fast-forward and a merge that adds nothing`() {
    val empty = DocumentBranch.createBranch("", agent("a"))
    val ahead = empty.applyOp(insertOp(0, "x"))
    assertTrue(empty.version().isRoot())
    assertFalse(ahead.version().isRoot())
    // A fast-forward takes the version of the other branch, and a merge that adds nothing keeps
    // its own.
    assertEquals(ahead.version(), empty.merge(ahead).version())
    assertEquals(ahead.version(), ahead.merge(empty).version())
  }

  @Test
  fun `a merge that brings back the agent's own edit keeps its seqs going`() {
    // The descendant holds an edit of this agent that the base value never saw. So the next seq
    // must come from the merged history, or the next edit would reuse the id of that edit.
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val descendant = base.applyOp(insertOp(3, "x"))
    val edited = base.merge(descendant).applyOp(insertOp(0, "y"))
    assertEquals("yabcx", edited.string())
    assertEquals(edited.string(), edited.graph().replay().string())
    val other = base.fork(agent("b")).applyOp(insertOp(1, "Z"))
    assertEquals("yaZbcx", edited.merge(other).string())
    assertEquals("yaZbcx", other.merge(edited).string())
  }

  @Test
  fun `a merge keeps the agent of the receiver`() {
    val base = DocumentBranch.createBranch("abc", agent("a"))
    val a = base.applyOp(insertOp(0, "1"))
    val b = base.fork(agent("b")).applyOp(insertOp(3, "2"))
    assertEquals(agent("a"), a.merge(b).agent())
    assertEquals(agent("b"), b.merge(a).agent())
    // A fast-forward takes the text of the other branch, but not its agent.
    assertEquals(agent("a"), base.merge(b).agent())
  }

  @Test
  fun `an insert after a delete at one place stays before a concurrent insert there`() {
    // The delete leaves an item of zero width at the place of "X". The cached cursor sits after
    // that item, so the insert must take the earliest boundary, before it. The reference
    // implementation gives "aXY" in both merge orders too.
    val base = DocumentBranch.createBranch("ab", agent("base"))
    val a = base.fork(agent("a")).applyOp(deleteOp(1, 1)).applyOp(insertOp(1, "X"))
    val b = base.fork(agent("b")).applyOp(insertOp(1, "Y"))
    val ab = a.merge(b)
    assertEquals("aXY", ab.string())
    assertEquals("aXY", b.merge(a).string())
    assertEquals("aXY", ab.graph().replay().string())
  }

  @Test
  fun `concurrent inserts order as Fugue and not as FugueMax`() {
    // The smallest history that a random search found where the two variants differ. FugueMax
    // gives "bcead". The reference implementation gives "becad", and this port must too.
    val empty = DocumentBranch.createBranch("", agent("base"))
    val c = empty.fork(agent("c")).applyOp(insertOp(0, "a"))
    val b1 = empty.fork(agent("b")).applyOp(insertOp(0, "b"))
    val b2 = b1.applyOp(insertOp(1, "c"))
    val a = c.fork(agent("a")).merge(b1).applyOp(insertOp(2, "d")).applyOp(insertOp(1, "e"))
    assertEquals("bead", a.string())
    val ab = a.merge(b2)
    assertEquals("becad", ab.string())
    assertEquals("becad", b2.merge(a).string())
    assertEquals("becad", ab.graph().replay().string())
  }
}

internal fun agent(name: String): Agent = Agent.createAgent(name)

internal fun DocumentBranch.string(): String = text().string()

internal fun DocumentBranch.length(): Int = text().length()

internal fun insertOp(offset: Int, fragment: CharSequence): DocumentOp.Insert = DocumentOp.insertOp(offset, fragment)

internal fun deleteOp(offset: Int, length: Int): DocumentOp.Delete = DocumentOp.deleteOp(offset, length)

internal fun modStampOp(stamp: Long, incSequence: Boolean): DocumentOp.ModStamp {
  return DocumentOp.modStampOp(stamp, incSequence)
}

internal fun cleanLinesOp(
  startLine: Int,
  endLine: Int,
  exceptLines: IntArray,
): DocumentOp.CleanLines {
  return DocumentOp.cleanLinesOp(startLine, endLine, exceptLines)
}

/**
 * The ops of a text move in [text], as `DocumentEx.moveText` makes them: an insert of the copy at
 * [dst], then a delete of the source.
 */
internal fun moveOps(text: CharSequence, srcStart: Int, srcEnd: Int, dst: Int): List<DocumentOp.Text> {
  val fragment = text.subSequence(srcStart, srcEnd).toString()
  var sourceStart = srcStart
  if (dst < srcStart) {
    // The copy lands before the source and shifts it.
    sourceStart += fragment.length
  }
  val copy = DocumentOp.insertOp(dst, fragment, sourceStart)
  val removal = DocumentOp.deleteOp(sourceStart, fragment.length, dst)
  return listOf(copy, removal)
}

internal fun DocumentBranch.moveText(srcStart: Int, srcEnd: Int, dst: Int): DocumentBranch {
  val ops = moveOps(text().chars(), srcStart, srcEnd, dst)
  return ops.fold(this) { branch, op -> branch.applyOp(op) }
}

/**
 * The ops of a move of up to three characters of [text] to a random offset outside them. The list is
 * empty when no such offset exists.
 */
internal fun randomMoveOps(random: Random, text: CharSequence): List<DocumentOp.Text> {
  val length = text.length
  if (length == 0) {
    return emptyList()
  }
  val srcStart = random.nextInt(length)
  val srcEnd = srcStart + 1 + random.nextInt(minOf(3, length - srcStart))
  val outside = srcStart + (length - srcEnd)
  if (outside == 0) {
    return emptyList()
  }
  // The offsets before the source come first, then the offsets after it.
  val pick = random.nextInt(outside)
  var dst = pick
  if (pick >= srcStart) {
    dst = srcEnd + 1 + (pick - srcStart)
  }
  return moveOps(text, srcStart, srcEnd, dst)
}

/**
 * Runs one round of a fuzz test with a [Random] of its own [seed], so a failing round
 * replays alone. A failure names the seed and the round.
 */
internal fun fuzzRound(seed: Long, round: Int, body: (Random) -> Unit) {
  try {
    body(Random(seed + round))
  } catch (e: Throwable) {
    throw AssertionError("The fuzz round $round with the seed ${seed + round} failed: ${e.message}", e)
  }
}

/**
 * This text with [ops] applied one after another, as [DocMerge.ops] says an editor applies them.
 */
internal fun DocumentText.afterOps(ops: List<DocumentOp.Text>): DocumentText = ops.fold(this) { text, op -> text.applyOp(op) }

/**
 * Fails unless every op of [ops] with a move offset is half of a pair that meets the contract of
 * `DocumentEvent.getMoveOffset`, when the ops apply to [start] one after another.
 */
internal fun assertMovePairs(start: DocumentText, ops: List<DocumentOp.Text>) {
  var text = start
  var pairedDelete: DocumentOp.Delete? = null
  for (op in ops) {
    val expectedDelete = pairedDelete
    pairedDelete = null
    text = text.applyOp(op)
    if (expectedDelete != null) {
      assertEquals(expectedDelete, op) { "the delete of a move" }
    } else if (op.moveOffset() != op.offset()) {
      pairedDelete = pairedDeleteOf(op, text)
    }
  }
  assertEquals(null, pairedDelete) { "a move insert without its delete" }
}

/**
 * The delete that must follow the move op [op]. [after] is the text after [op], and the source there
 * must hold the fragment. A move delete that no insert opened fails.
 */
private fun pairedDeleteOf(op: DocumentOp.Text, after: DocumentText): DocumentOp.Delete {
  return when (op) {
    is DocumentOp.Insert -> {
      val sourceStart = op.moveOffset()
      val fragment = op.fragment().toString()
      assertEquals(fragment, after.string(sourceStart, sourceStart + op.length())) { "the source of $op" }
      DocumentOp.deleteOp(op.moveOffset(), op.length(), op.offset())
    }
    is DocumentOp.Delete -> fail("a move delete without its insert: $op")
  }
}

internal fun assertSameText(expected: DocumentText, actual: DocumentText) {
  assertEquals(expected.string(), actual.string())
  assertEquals(expected.chars().toString(), actual.chars().toString())
  assertEquals(expected.length(), actual.length())
  assertEquals(expected.lineCount(), actual.lineCount())
  for (line in 0 until expected.lineCount()) {
    assertEquals(expected.lineStartOffset(line), actual.lineStartOffset(line)) { "lineStartOffset($line)" }
    assertEquals(expected.lineEndOffset(line), actual.lineEndOffset(line)) { "lineEndOffset($line)" }
    assertEquals(expected.lineSeparatorLength(line), actual.lineSeparatorLength(line)) { "lineSeparatorLength($line)" }
  }
  for (offset in 0 until expected.length()) {
    assertEquals(expected.lineNumber(offset), actual.lineNumber(offset)) { "lineNumber($offset)" }
  }
  if (expected.length() > 2) {
    val endOffset = expected.length() - 1
    assertEquals(expected.string(1, endOffset), actual.string(1, endOffset))
  }
}
