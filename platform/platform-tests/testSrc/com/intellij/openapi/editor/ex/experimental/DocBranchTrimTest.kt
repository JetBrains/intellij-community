// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Tests [DocumentBranch.trimmedTo] and the merges of a trimmed branch. A merge that the safepoint rule
 * allows must give the text of the same merge without the trim, and every other merge must throw.
 */
class DocBranchTrimTest {

  @Test
  fun `a trim keeps the text, the mod state, the agent and the version`() {
    val branch = DocumentBranch.createBranch("abc", agent("main")).applyOp(insertOp(3, "d"))
    val trimmed = branch.trimmedTo(branch.version())
    assertSame(branch.text(), trimmed.text())
    assertSame(branch.modState(), trimmed.modState())
    assertSame(branch.agent(), trimmed.agent())
    assertEquals(branch.version(), trimmed.version())
    assertFalse(trimmed.version().isRoot())
    assertEquals(0, trimmed.graph().size())
    assertEquals(0, trimmed.graph().runCount())
    assertEquals("abcd", trimmed.graph().replay().string())
  }

  @Test
  fun `a trim to the base version keeps the branch`() {
    val branch = DocumentBranch.createBranch("abc", agent("main"))
    assertSame(branch, branch.trimmedTo(branch.version()))
    val edited = branch.applyOp(insertOp(3, "d"))
    val trimmed = edited.trimmedTo(edited.version())
    assertSame(trimmed, trimmed.trimmedTo(trimmed.version()))
  }

  @Test
  fun `a trim below the version is not supported yet`() {
    val older = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val branch = older.applyOp(insertOp(3, "d"))
    val failure = assertThrows(IllegalArgumentException::class.java) {
      branch.trimmedTo(older.version())
    }
    assertFalse(failure is TrimmedHistoryException)
  }

  @Test
  fun `a version below the base version neither replays nor takes an append`() {
    val older = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val branch = older.applyOp(insertOp(3, "d"))
    val graph = branch.trimmedTo(branch.version()).graph()
    assertEquals("abcd", graph.replay(branch.version()).string())
    for (below in listOf(Version.root(), older.version())) {
      assertThrows(TrimmedHistoryException::class.java, {
        graph.replay(below)
      }, "$below")
    }
    val event = Event.createInsert(agent("other"), 0, 0, "x")
    assertThrows(TrimmedHistoryException::class.java) {
      graph.append(event, Version.root())
    }
  }

  @Test
  fun `edits after a trim merge with a fork made at the safepoint`() {
    val main = DocumentBranch.createBranch("abc", agent("main")).applyOp(insertOp(3, "d"))
    val fork = main.fork(agent("fork")).applyOp(insertOp(0, "F"))
    val trimmed = main.trimmedTo(main.version()).applyOp(insertOp(4, "M"))
    val forward = trimmed.merge(fork)
    val backward = fork.merge(trimmed)
    assertEquals("FabcdM", forward.string())
    assertEquals("FabcdM", backward.string())
    assertEquals("FabcdM", forward.graph().replay().string())
    assertEquals("FabcdM", backward.graph().replay().string())
  }

  @Test
  fun `a trimmed branch with no events fast-forwards to a fork`() {
    val main = DocumentBranch.createBranch("abc", agent("main")).applyOp(insertOp(3, "d"))
    val fork = main.fork(agent("fork")).applyOp(insertOp(0, "F"))
    val trimmed = main.trimmedTo(main.version())
    val merge = trimmed.mergeWithOps(fork)
    assertSame(fork.text(), merge.branch().text())
    assertEquals(listOf(DocumentOp.insertOp(0, "F")), merge.ops())
  }

  @Test
  fun `a fork of a trimmed branch merges back`() {
    val main = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val trimmed = main.trimmedTo(main.version())
    val fork = trimmed.fork(agent("fork")).applyOp(insertOp(0, "F"))
    val edited = trimmed.applyOp(deleteOp(1, 1))
    assertEquals("Fac", edited.merge(fork).string())
    assertEquals("Fac", fork.merge(edited).string())
  }

  /**
   * The fork saw "abc". The main branch then typed "d", trimmed, and typed "e". No graph holds the
   * event of "d": the fork never got it, and the trim dropped it into the base text "abcd". So the
   * fork does not hold the base version, and a merge in either direction throws.
   */
  @Test
  fun `a stale fork merges in neither direction`() {
    val main = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val stale = main.fork(agent("stale"))
    val ahead = main.applyOp(insertOp(3, "d"))
    val untrimmed = ahead.applyOp(insertOp(0, "e"))
    val trimmed = ahead.trimmedTo(ahead.version()).applyOp(insertOp(0, "e"))
    assertEquals("abc", stale.string())
    assertEquals("eabcd", trimmed.string())
    assertEquals("eabcd", untrimmed.string())

    // Without the trim, each op of the merge comes from an event: "d" and then "e".
    val merge = stale.mergeWithOps(untrimmed)
    assertEquals("eabcd", merge.branch().string())
    val opsFromEvents = listOf(
      DocumentOp.insertOp(3, "d"),
      DocumentOp.insertOp(0, "e"),
    )
    assertEquals(opsFromEvents, merge.ops())

    // The trimmed graph starts from the base text "abcd", and it holds only the event of "e".
    assertEquals(1, trimmed.graph().size())
    assertEquals("abcd", trimmed.graph().replay(ahead.version()).string())
    // The version of the fork lies below the base version, so no replay can start there.
    assertThrows(TrimmedHistoryException::class.java) {
      trimmed.graph().replay(stale.version())
    }

    // A merge would need "insert d at 3", which no event gives. Both directions throw, even though
    // the fork adds nothing.
    assertThrows(TrimmedHistoryException::class.java) {
      stale.merge(trimmed)
    }
    assertThrows(TrimmedHistoryException::class.java) {
      trimmed.merge(stale)
    }
  }

  @Test
  fun `a fork that edited before the safepoint merges in neither direction`() {
    val main = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val fork = main.fork(agent("fork")).applyOp(insertOp(0, "F"))
    val ahead = main.applyOp(insertOp(3, "d"))
    val trimmed = ahead.trimmedTo(ahead.version())
    assertThrows(TrimmedHistoryException::class.java) {
      trimmed.merge(fork)
    }
    assertThrows(TrimmedHistoryException::class.java) {
      fork.merge(trimmed)
    }
    // The branch before the trim still merges the fork.
    assertEquals("Fabcd", ahead.merge(fork).string())
  }

  /**
   * The fork holds the safepoint, but its own edit is concurrent with it. So the base version of the
   * trimmed branch is no critical version of the fork, and both directions throw.
   */
  @Test
  fun `a fork that merged the safepoint after an older edit merges in neither direction`() {
    val main = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val fork = main.fork(agent("fork")).applyOp(insertOp(0, "F"))
    val ahead = main.applyOp(insertOp(3, "d"))
    val holdsSafepoint = fork.merge(ahead)
    val trimmed = ahead.trimmedTo(ahead.version())
    val trimmedEdited = trimmed.applyOp(insertOp(4, "M"))
    for (other in listOf(trimmed, trimmedEdited)) {
      assertThrows(TrimmedHistoryException::class.java) {
        other.merge(holdsSafepoint)
      }
      assertThrows(TrimmedHistoryException::class.java) {
        holdsSafepoint.merge(other)
      }
    }
    // The branch before the trim still merges the fork.
    assertEquals("Fabcd", ahead.merge(holdsSafepoint).string())
  }

  @Test
  fun `a run that follows both heads of a two-head base version merges`() {
    val joined = joinedBranch()
    val fork = joined.fork(agent("fork")).applyOp(insertOp(2, "F"))
    val trimmed = joined.trimmedTo(joined.version())
    assertEquals(joined.version(), trimmed.version())
    val trimmedEdited = trimmed.applyOp(insertOp(4, "M"))
    val untrimmedEdited = joined.applyOp(insertOp(4, "M"))
    val expected = untrimmedEdited.merge(fork).string()
    assertEquals(expected, trimmedEdited.merge(fork).string())
    assertEquals(expected, fork.merge(trimmedEdited).string())
  }

  @Test
  fun `a run that follows one head of a two-head base version merges in neither direction`() {
    val start = DocumentBranch.createBranch("ab", agent("start"))
    val left = start.fork(agent("left")).applyOp(insertOp(0, "L"))
    val right = start.fork(agent("right")).applyOp(insertOp(2, "R"))
    val joined = left.merge(right)
    val trimmed = joined.trimmedTo(joined.version())
    val oneHead = left.fork(agent("fork")).applyOp(insertOp(1, "F"))
    assertThrows(TrimmedHistoryException::class.java) {
      trimmed.merge(oneHead)
    }
    assertThrows(TrimmedHistoryException::class.java) {
      oneHead.merge(trimmed)
    }
  }

  /**
   * The trimmed branch names the parent of "d" by its empty parent set, and the fork names it by the
   * lv of "c". The id check must see one parent there, and no clash.
   */
  @Test
  fun `the id check reads the parents of a trimmed graph as its base version`() {
    val main = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val fork = main.fork(agent("fork"))
    val trimmed = main.trimmedTo(main.version()).applyOp(insertOp(3, "d"))
    val caughtUp = fork.merge(trimmed).applyOp(insertOp(0, "F"))
    assertEquals("Fabcd", trimmed.merge(caughtUp).string())
  }

  /**
   * The main branch types "abc" as one run. The fork saw "ab" and trimmed there, so the run crosses
   * the base version of the fork, and the merge takes only its suffix.
   */
  @Test
  fun `a run that crosses the base version of the receiver merges its suffix`() {
    val typed = DocumentBranch.createBranch("", agent("main"))
      .applyOp(insertOp(0, "a"))
      .applyOp(insertOp(1, "b"))
    val fork = typed.fork(agent("fork"))
    val trimmedFork = fork.trimmedTo(fork.version()).applyOp(insertOp(0, "F"))
    val typedOn = typed.applyOp(insertOp(2, "c"))
    assertEquals(1, typedOn.graph().runCount())
    assertEquals("Fabc", trimmedFork.merge(typedOn).string())
    assertEquals("Fabc", typedOn.merge(trimmedFork).string())
  }

  /**
   * Two branches break the agent contract after a trim: both mint the next seq of the main agent.
   * The id check samples the first seq that both graphs store, so it finds each clash.
   */
  @Test
  fun `the id check finds a clash after a trim`() {
    val main = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val trimmed = main.trimmedTo(main.version())
    val x = trimmed.applyOp(insertOp(0, "x"))
    // Another character under the same id.
    val y = trimmed.applyOp(insertOp(0, "y"))
    // The same insert under the same id, but after an edit of another agent and not at the base.
    val other = trimmed.fork(agent("other")).applyOp(insertOp(3, "o"))
    val afterOther = other.fork(agent("main")).applyOp(insertOp(0, "x"))
    for (clashing in listOf(y, afterOther)) {
      assertThrows(EventIdClashException::class.java) {
        x.merge(clashing)
      }
      assertThrows(EventIdClashException::class.java) {
        clashing.merge(x)
      }
    }
  }

  @Test
  fun `two trims in a row merge with a fork made between them`() {
    val first = DocumentBranch.createBranch("ab", agent("main")).applyOp(insertOp(2, "c"))
    val once = first.trimmedTo(first.version()).applyOp(insertOp(3, "d"))
    val fork = once.fork(agent("fork")).applyOp(insertOp(0, "F"))
    val twice = once.trimmedTo(once.version()).applyOp(insertOp(4, "e"))
    assertEquals("Fabcde", twice.merge(fork).string())
    assertEquals("Fabcde", fork.merge(twice).string())
  }

  @Test
  fun `a trim keeps the mod state of a fast-forward`() {
    val base = DocumentBranch.createBranch("ab", agent("base"))
    val descendant = base.fork(agent("fork")).applyOp(insertOp(2, "c"))
    val forwarded = base.merge(descendant)
    val trimmed = forwarded.trimmedTo(forwarded.version())
    assertSame(forwarded.modState(), trimmed.modState())
  }

  /**
   * Two forks that typed at the start of "ab", merged: a branch whose version has two heads.
   */
  private fun joinedBranch(): DocumentBranch {
    val start = DocumentBranch.createBranch("ab", agent("start"))
    val left = start.fork(agent("left")).applyOp(insertOp(0, "L"))
    val right = start.fork(agent("right")).applyOp(insertOp(2, "R"))
    return left.merge(right)
  }
}
