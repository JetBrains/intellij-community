// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the tip coalescing of [EventGraph.append]: an append that continues the newest run
 * extends it and adds no run.
 *
 * Coalescing must never change a unit, an id, or a parent. So a test checks the replayed text
 * as well as [EventGraph.runCount], and a [History] also replays every version it passed.
 */
class TipCoalescingTest {

  // ------------------------------------------------------------------------------ what continues

  @Test
  fun `keystrokes at the end of the run extend it`() {
    val history = History().type(U, 0, "abc")
    assertEquals(1, history.graph().runCount())
    assertEquals(3, history.graph().size())
    history.assertReplays("abc")
  }

  @Test
  fun `an insert of several characters at the end of the run extends it`() {
    val history = History().insert(U, 0, "ab").insert(U, 2, "cde")
    assertEquals(1, history.graph().runCount())
    history.assertReplays("abcde")
  }

  @Test
  fun `the Delete key extends a delete run`() {
    val history = History().insert(V, 0, "abcdef")
    repeat(3) {
      history.delete(U, 1, 1)
    }
    assertEquals(2, history.graph().runCount()) // the text, and one delete run
    history.assertReplays("aef")
  }

  /**
   * The units of a backspace run walk BACKWARDS, and a delete run holds units that all edit at
   * one offset. So a backspace cannot extend the run before it. This pins the limit.
   */
  @Test
  fun `a backspace starts a run of its own`() {
    val history = History().insert(V, 0, "abcdef")
    history.delete(U, 4, 1).delete(U, 3, 1).delete(U, 2, 1)
    assertEquals(4, history.graph().runCount())
    history.assertReplays("abf")
  }

  // ------------------------------------------------------------------------ what does not continue

  @Test
  fun `an insert that does not start at the end of the run starts a new run`() {
    // The run "ab" covers the offsets 1 and 2, so it ends at 3. Each case is one offset
    // before, inside, or past that end.
    for ((offset, expected) in listOf(1 to "xYabxxx", 2 to "xaYbxxx", 4 to "xabxYxx")) {
      val history = History().insert(V, 0, "xxxx").insert(U, 1, "ab").insert(U, offset, "Y")
      assertEquals(3, history.graph().runCount()) { "offset $offset" }
      history.assertReplays(expected)
    }
  }

  @Test
  fun `a change of the op kind starts a new run`() {
    val history = History().insert(U, 0, "abc")
    // A delete of the last character of the insert run, then an insert at the offset of the
    // delete run.
    history.delete(U, 2, 1).insert(U, 2, "d")
    assertEquals(3, history.graph().runCount())
    history.assertReplays("abd")
  }

  @Test
  fun `a half of a move never extends a run`() {
    // The move insert starts at the end of the typed run.
    val inserts = History()
      .insert(V, 0, "xy")
      .type(U, 2, "ab")
      .move(U, 0, 2, 4)
    assertEquals(4, inserts.graph().runCount())
    inserts.assertReplays("abxy")
    // The move delete starts at the offset of the delete run.
    val moveDelete = DocumentOp.deleteOp(0, 2, 2)
    val deletes = History()
      .insert(V, 0, "xabab")
      .delete(U, 0, 1)
      .apply(U, moveDelete)
    assertEquals(3, deletes.graph().runCount())
    deletes.assertReplays("ab")
  }

  @Test
  fun `nothing extends a half of a move`() {
    // A keystroke at the end of a move insert.
    val moveInsert = DocumentOp.insertOp(2, "ab", 0)
    val inserts = History()
      .insert(V, 0, "ab")
      .apply(U, moveInsert)
      .insert(U, 4, "c")
    assertEquals(3, inserts.graph().runCount())
    inserts.assertReplays("ababc")
    // A Delete key press at the offset of a move delete.
    val deletes = History()
      .insert(V, 0, "abcd")
      .move(U, 0, 2, 4)
      .delete(U, 0, 1)
    assertEquals(4, deletes.graph().runCount())
    deletes.assertReplays("dab")
  }

  @Test
  fun `another agent starts a new run, and so does the first agent after it`() {
    val history = History().insert(U, 0, "ab").insert(V, 2, "c").insert(U, 3, "d")
    assertEquals(3, history.graph().runCount())
    history.assertReplays("abcd")
  }

  /**
   * A later unit of a run has the unit before it as its only parent. An append at any other
   * version is concurrent with some unit, or merges two heads. So it cannot extend the run,
   * even when its agent, its seq, and its offset all line up.
   */
  @Test
  fun `an append at a version other than the run end starts a new run`() {
    // A fork from inside the run: the parent is "b", and the run already holds "c".
    val base = EventGraph.createGraph().append(Event.createInsert(V, 0, 0, "xxxxxx"), Version.root())
    var fork = base.append(Event.createInsert(U, 0, 0, "ab"), base.version())
    val afterAb = fork.version()
    fork = fork.append(Event.createInsert(U, 2, 2, "c"), fork.version())
    assertEquals(2, fork.runCount())
    fork = fork.append(Event.createInsert(U, 3, 3, "d"), afterAb)
    assertEquals(3, fork.runCount())
    assertEquals("abcxdxxxxx", fork.replay().string())

    // A merge point: the version has two heads, and one of them is the end of the run.
    var merge = EventGraph.createGraph().append(Event.createInsert(V, 0, 0, "X"), Version.root())
    merge = merge.append(Event.createInsert(U, 0, 0, "ab"), Version.root())
    assertEquals("v[(u, 1), (v, 0)]", merge.version().toString())
    merge = merge.append(Event.createInsert(U, 2, 2, "c"), merge.version())
    assertEquals(3, merge.runCount())
    assertEquals("abcX", merge.replay().string())
  }

  // --------------------------------------------------------------------------------- the limits

  @Test
  fun `an insert run stops growing at the limit`() {
    val limit = EventGraph.MAX_COALESCED_INSERT
    val history = History().type(U, 0, "x".repeat(limit))
    assertEquals(1, history.graph().runCount())
    // The next keystroke would take the run past the limit, so it starts a new run.
    history.type(U, limit, "yz")
    assertEquals(2, history.graph().runCount())
    history.assertReplays("x".repeat(limit) + "yz")
  }

  @Test
  fun `an insert that would pass the limit starts a new run, and one that meets it extends`() {
    val limit = EventGraph.MAX_COALESCED_INSERT
    val passing = History().insert(U, 0, "x".repeat(limit - 1)).insert(U, limit - 1, "yz")
    assertEquals(2, passing.graph().runCount())
    passing.assertReplays("x".repeat(limit - 1) + "yz")

    val meeting = History().insert(U, 0, "x".repeat(limit - 1)).insert(U, limit - 1, "y")
    assertEquals(1, meeting.graph().runCount())
    meeting.assertReplays("x".repeat(limit - 1) + "y")
  }

  @Test
  fun `an event longer than the limit stays one run, and nothing extends it`() {
    val length = EventGraph.MAX_COALESCED_INSERT + 10
    val history = History().insert(U, 0, "x".repeat(length))
    assertEquals(1, history.graph().runCount())
    history.insert(U, length, "y")
    assertEquals(2, history.graph().runCount())
    history.assertReplays("x".repeat(length) + "y")
  }

  /**
   * A delete run holds no characters, so an extension copies nothing, and no limit applies.
   */
  @Test
  fun `a delete run grows past the insert limit`() {
    val length = EventGraph.MAX_COALESCED_INSERT + 10
    val history = History().insert(V, 0, "x".repeat(length + 1))
    repeat(length) {
      history.delete(U, 0, 1)
    }
    assertEquals(2, history.graph().runCount())
    history.assertReplays("x")
  }

  /**
   * An event rejects an offset range past [Int.MAX_VALUE]. A delete run grows its length at a
   * fixed offset, so an extension could create such an event from two legal ones. The append
   * must then start a new run and not fail. The raw API does not check an offset against the
   * document, so these graphs never replay.
   */
  @Test
  fun `a delete run stops before its offset range overflows`() {
    val offset = Int.MAX_VALUE - 3
    var graph = EventGraph.createGraph().append(Event.createDelete(U, 0, offset, 1), Version.root())
    // The joined range ends exactly at Int.MAX_VALUE, which is still legal.
    graph = graph.append(Event.createDelete(U, 1, offset, 2), graph.version())
    assertEquals(1, graph.runCount())
    // One more unit would pass it, although the event alone is legal.
    graph = graph.append(Event.createDelete(U, 3, offset, 1), graph.version())
    assertEquals(2, graph.runCount())
    assertEquals(4, graph.size())
  }

  // ------------------------------------------------------------------------ values and versions

  /**
   * A graph value keeps its newest run itself, so two siblings of one value extend that run
   * each on their own. When a sibling closes the run into its trees, the other one and
   * the older value must not see it.
   */
  @Test
  fun `siblings extend one run each on their own`() {
    val older = EventGraph.createGraph().append(Event.createInsert(U, 0, 0, "ab"), Version.root())
    var newer = older.append(Event.createInsert(U, 2, 2, "c"), older.version())
    var sibling = older.append(Event.createInsert(U, 2, 2, "C"), older.version())
    assertEquals(1, newer.runCount())
    assertEquals(1, sibling.runCount())
    assertEquals("abc", newer.replay().string())
    assertEquals("abC", sibling.replay().string())

    // Each sibling closes its run into its own trees, which share their older nodes.
    newer = newer.append(Event.createInsert(U, 3, 0, "N"), newer.version())
    sibling = sibling.append(Event.createInsert(U, 3, 0, "S"), sibling.version())
    assertEquals(2, newer.runCount())
    assertEquals(2, sibling.runCount())
    assertEquals("Nabc", newer.replay().string())
    assertEquals("SabC", sibling.replay().string())
    assertEquals("abc", newer.replay(Version.of(2)).string())
    assertEquals("abC", sibling.replay(Version.of(2)).string())
    // The older value did not change.
    assertEquals(1, older.runCount())
    assertEquals(2, older.size())
    assertEquals("ab", older.replay().string())
  }

  /**
   * A version that names a unit inside a run replays, before and after the run is closed.
   */
  @Test
  fun `a past version inside a run replays after the run is closed`() {
    val history = History().type(U, 0, "hello")
    history.assertReplays("hello")
    // A jump to the front closes the run, and more typing makes a second one.
    history.type(U, 0, "> ").type(U, 7, "!")
    assertEquals(3, history.graph().runCount())
    history.assertReplays("> hello!")
  }

  @Test
  fun `the next seq of an agent comes from the newest run`() {
    val graph = History().type(U, 0, "abc").graph()
    assertThrows(IllegalArgumentException::class.java) {
      graph.append(Event.createInsert(U, 2, 3, "d"), graph.version())
    }
    assertEquals(4, graph.append(Event.createInsert(U, 3, 3, "d"), graph.version()).size())
  }

  // ------------------------------------------------------------------------------------- merges

  /**
   * A merge appends a suffix through the same append as a local edit, so it coalesces too.
   */
  @Test
  fun `a merged suffix extends the run it continues`() {
    val prefix = History().type(U, 0, "ab").graph()
    val whole = prefix.append(Event.createInsert(U, 2, 2, "c"), prefix.version())
    val caughtUp = prefix.mergeFrom(whole)
    assertEquals(1, caughtUp.runCount())
    assertEquals("abc", caughtUp.replay().string())
    assertEquals(1, whole.mergeFrom(prefix).runCount())
  }

  @Test
  fun `a merged suffix after a local edit starts a new run`() {
    val prefix = History().type(U, 0, "ab").graph()
    val whole = prefix.append(Event.createInsert(U, 2, 2, "c"), prefix.version())
    val edited = prefix.append(Event.createInsert(V, 0, 2, "X"), prefix.version())
    val forward = edited.mergeFrom(whole)
    val backward = whole.mergeFrom(edited)
    // The suffix "c" has the parent "b", which is no longer the end of the newest run.
    assertEquals(3, forward.runCount())
    assertEquals(2, backward.runCount())
    assertEquals("abcX", forward.replay().string())
    assertEquals("abcX", backward.replay().string())
  }

  /**
   * One replica holds the three units of one agent in one run. The other holds them in two
   * runs, because a run of another agent arrived between them. The id check samples the two
   * ends of the shared range, and here those ends sit in different runs on one side.
   */
  @Test
  fun `replicas that cut one agent into different runs converge`() {
    val prefix = History().type(U, 0, "ab").graph()
    val whole = prefix.append(Event.createInsert(U, 2, 2, "c"), prefix.version())
    val withX = prefix.append(Event.createInsert(V, 0, 2, "X"), prefix.version())
    val cut = withX.mergeFrom(whole)
    val oneRun = whole.mergeFrom(withX)
    assertEquals(3, cut.runCount())
    assertEquals(2, oneRun.runCount())
    val expected = cut.replay().string()
    assertEquals(expected, oneRun.replay().string())
    assertEquals(expected, cut.mergeFrom(oneRun).replay().string())
    assertEquals(expected, oneRun.mergeFrom(cut).replay().string())
  }

  /**
   * The id check reads the newest run of each graph. Two graphs that hold the same units only
   * in their newest runs must merge, and a difference inside them must be caught as a clash.
   */
  @Test
  fun `the id check reads the newest runs`() {
    val origin = EventGraph.createGraph()
    val mine = History(origin).type(U, 0, "abc").graph()
    val same = History(origin).type(U, 0, "abc").graph()
    assertEquals("abc", mine.mergeFrom(same).replay().string())
    assertEquals("abc", same.mergeFrom(mine).replay().string())

    for (clash in listOf("Zbc", "abZ")) {
      val theirs = History(origin).type(U, 0, clash).graph()
      val failure = assertThrows(EventIdClashException::class.java) { mine.mergeFrom(theirs) }
      assertTrue(failure.message.orEmpty().contains("the inserted character")) { "$clash: ${failure.message}" }
      assertThrows(EventIdClashException::class.java) { theirs.mergeFrom(mine) }
    }
  }

  // ----------------------------------------------------------------------------------- branches

  @Test
  fun `typing in a branch costs one run per burst`() {
    var branch = DocBranch.createBranch("", U)
    for ((offset, char) in "hello\nworld".withIndex()) {
      branch = branch.applyOp(insertOp(offset, char.toString()))
    }
    assertEquals(1, branch.graph().runCount())
    // A jump to the front, then the Delete key twice, then a backspace twice at the end.
    branch = branch.applyOp(insertOp(0, "> "))
    branch = branch.applyOp(deleteOp(2, 1)).applyOp(deleteOp(2, 1))
    repeat(2) {
      branch = branch.applyOp(deleteOp(branch.length() - 1, 1))
    }
    assertEquals(5, branch.graph().runCount())
    assertEquals("> llo\nwor", branch.string())
    assertEquals(branch.string(), branch.graph().replay().string())
  }

  /**
   * A run that starts at a merge point keeps both parents on its first unit. Every later unit
   * still has the unit before it as its only parent, so the typing after a merge extends it.
   */
  @Test
  fun `typing after a merge extends the run that starts at the merge point`() {
    val base = DocBranch.createBranch("ab", agent("base"))
    val left = base.fork(U).applyOp(insertOp(0, "L"))
    val right = base.fork(V).applyOp(insertOp(2, "R"))
    var merged = left.merge(right)
    assertEquals(2, merged.graph().runCount())
    val caret = merged.length()
    for ((i, char) in "xyz".withIndex()) {
      merged = merged.applyOp(insertOp(caret + i, char.toString()))
    }
    assertEquals(3, merged.graph().runCount())
    assertEquals("LabRxyz", merged.string())
    assertEquals(merged.string(), merged.graph().replay().string())
    // The other side receives the run with its two-parent first unit, and converges.
    val synced = right.merge(merged)
    assertEquals(merged.string(), synced.string())
    assertEquals(synced.string(), synced.graph().replay().string())
  }

  @Test
  fun `concurrent typing bursts merge and converge`() {
    val base = DocBranch.createBranch("0123456789", agent("base"))
    var left = base.fork(agent("left"))
    var right = base.fork(agent("right"))
    for ((i, char) in "abc".withIndex()) {
      left = left.applyOp(insertOp(2 + i, char.toString()))
      right = right.applyOp(insertOp(8 + i, char.uppercase()))
    }
    assertEquals(1, left.graph().runCount())
    assertEquals(1, right.graph().runCount())
    val forward = left.merge(right)
    val backward = right.merge(left)
    assertEquals("01abc234567ABC89", forward.string())
    assertEquals(forward.string(), backward.string())
    assertEquals(forward.string(), forward.graph().replay().string())
    assertEquals(2, forward.graph().runCount())
  }

  /**
   * A linear history and the text it must replay to. Every op appends at the graph's own
   * version, under the seq that comes next for its agent. The history keeps each version it
   * passes, with the text it held there. It grows from [origin], which must have no events.
   */
  private class History(origin: EventGraph = EventGraph.createGraph()) {
    private var graph = origin
    private val text = StringBuilder()
    private val nextSeqs = HashMap<Agent, Int>()
    private val past = ArrayList<Pair<Version, String>>()

    fun graph(): EventGraph {
      return graph
    }

    fun insert(agent: Agent, offset: Int, fragment: String): History {
      return apply(agent, DocumentOp.insertOp(offset, fragment))
    }

    fun delete(agent: Agent, offset: Int, length: Int): History {
      return apply(agent, DocumentOp.deleteOp(offset, length))
    }

    /**
     * A text move, with the ops of [moveOps].
     */
    fun move(agent: Agent, srcStart: Int, srcEnd: Int, dst: Int): History {
      for (op in moveOps(text, srcStart, srcEnd, dst)) {
        apply(agent, op)
      }
      return this
    }

    fun apply(agent: Agent, op: DocumentOp.Text): History {
      val event = Event.create(agent, takeSeqs(agent, op.length()), op)
      graph = graph.append(event, graph.version())
      when (op) {
        is DocumentOp.Insert -> text.insert(op.offset(), op.fragment())
        is DocumentOp.Delete -> text.delete(op.offset(), op.offset() + op.length())
      }
      past.add(graph.version() to text.toString())
      return this
    }

    /**
     * One insert per character of [chars], each at the end of the one before it.
     */
    fun type(agent: Agent, offset: Int, chars: String): History {
      for ((i, char) in chars.withIndex()) {
        insert(agent, offset + i, char.toString())
      }
      return this
    }

    /**
     * Checks the text, and that every version this history passed replays to its text.
     */
    fun assertReplays(expected: String) {
      assertEquals(expected, text.toString())
      assertEquals(expected, graph.replay().string())
      for ((version, textThere) in past) {
        assertEquals(textThere, graph.replay(version).string()) { "at $version" }
      }
    }

    private fun takeSeqs(agent: Agent, count: Int): Int {
      val seq = nextSeqs[agent] ?: 0
      nextSeqs[agent] = seq + count
      return seq
    }
  }

  private companion object {
    val U = agent("u")
    val V = agent("v")
  }
}
