// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the box diagram that the `toString` of an event graph draws.
 *
 * Each test states the whole diagram, because the art is the thing under test and a reader
 * has to see it. A test that only counts lines instead covers a bound, where the exact art
 * carries nothing.
 *
 * The shapes below cover every part of the notation: a straight step, a fork, a merge, a
 * branch that ends, a branch that waits a row, a history with no common event, a line that
 * crosses a column, the base box of a base text, and the bounds that keep the diagram out of a log.
 */
class EventGraphDiagramTest {

  @Test
  fun `a linear history draws one box under the other`() {
    val u = agent("user1")
    var graph = EventGraph.createGraph()
    graph = graph.append(Event.createInsert(u, 0, 0, "abc"), graph.version())
    graph = graph.append(Event.createDelete(u, 3, 1, 1), graph.version())
    assertEquals(
      """
      EventGraph(units=4, runs=2, version=v[3])
      ┌───────────────┐
      │ insert "abc"  │
      │ offset 0      │
      │ lv 0..2       │
      │ agent "user1" │
      └───────┬───────┘
              │
      ┌───────┴───────┐
      │ delete 1 char │
      │ offset 1      │
      │ lv 3          │
      │ agent "user1" │
      └───────────────┘
      """.trimIndent(),
      graph.toString(),
    )
  }

  /**
   * Three keystrokes extend one run, so they draw as one box. A fork from inside that run
   * joins the box and not the unit, which is one of the two things the diagram cannot show.
   */
  @Test
  fun `typing draws one box, and a fork from inside it joins the box`() {
    val u1 = agent("user1")
    var graph = EventGraph.createGraph()
    for ((seq, char) in "abc".withIndex()) {
      graph = graph.append(Event.createInsert(u1, seq, seq, char.toString()), graph.version())
    }
    graph = graph.append(Event.createInsert(agent("user2"), 0, 1, "Z"), Version.of(0))
    assertEquals(
      """
      EventGraph(units=4, runs=2, version=v[2, 3])
      ┌───────────────┐
      │ insert "abc"  │
      │ offset 0      │
      │ lv 0..2       │
      │ agent "user1" │
      └───────┬───────┘
              │
      ┌───────┴───────┐
      │ insert "Z"    │
      │ offset 1      │
      │ lv 3          │
      │ agent "user2" │
      └───────────────┘
      """.trimIndent(),
      graph.toString(),
    )
  }

  @Test
  fun `a fork draws the branches side by side and a head keeps a plain border`() {
    val u1 = agent("user1")
    val u2 = agent("user2")
    var graph = EventGraph.createGraph()
    graph = graph.append(Event.createInsert(u1, 0, 0, "ab"), graph.version())
    val base = graph.version()
    // Neither "c" nor "d" starts at the end of the run before it, so each one is a run.
    graph = graph.append(Event.createInsert(u1, 2, 1, "c"), base)
    graph = graph.append(Event.createInsert(u1, 3, 1, "d"), graph.version())
    graph = graph.append(Event.createInsert(u2, 0, 2, "Z"), base)
    // The run of user2 has no child, so its lower border carries no join.
    assertEquals(
      """
      EventGraph(units=5, runs=4, version=v[3, 4])
      ┌───────────────┐
      │ insert "ab"   │
      │ offset 0      │
      │ lv 0..1       │
      │ agent "user1" │
      └───────┬───────┘
              │
              ├──────────────────┐
              │                  │
      ┌───────┴───────┐  ┌───────┴───────┐
      │ insert "c"    │  │ insert "Z"    │
      │ offset 1      │  │ offset 2      │
      │ lv 2          │  │ lv 4          │
      │ agent "user1" │  │ agent "user2" │
      └───────┬───────┘  └───────────────┘
              │
      ┌───────┴───────┐
      │ insert "d"    │
      │ offset 1      │
      │ lv 3          │
      │ agent "user1" │
      └───────────────┘
      """.trimIndent(),
      graph.toString(),
    )
  }

  @Test
  fun `a branch that skips a row keeps its line, and a merge closes it`() {
    val u1 = agent("user1")
    val u2 = agent("user2")
    val root = Version.root()
    val shared = EventGraph.createGraph().append(Event.createInsert(u1, 0, 0, "a"), root)
    // One side makes a single edit while the other makes two, so the short side waits one
    // row for its merge. Its column carries the line down beside the box it skips. No edit
    // starts at the end of the run before it, so each edit is a run.
    val shortSide = shared.append(Event.createInsert(u1, 1, 0, "b"), shared.version())
    var longSide = shared.append(Event.createInsert(u2, 0, 1, "X"), shared.version())
    longSide = longSide.append(Event.createInsert(u2, 1, 1, "Y"), longSide.version())
    val merged = shortSide.mergeFrom(longSide)
    assertEquals(
      """
      EventGraph(units=5, runs=5, version=v[4])
      ┌───────────────┐
      │ insert "a"    │
      │ offset 0      │
      │ lv 0          │
      │ agent "user1" │
      └───────┬───────┘
              │
              ├──────────────────┐
              │                  │
      ┌───────┴───────┐  ┌───────┴───────┐
      │ insert "b"    │  │ insert "X"    │
      │ offset 0      │  │ offset 1      │
      │ lv 1          │  │ lv 2          │
      │ agent "user1" │  │ agent "user2" │
      └───────┬───────┘  └───────┬───────┘
              │                  │
              │          ┌───────┴───────┐
              │          │ insert "Y"    │
              │          │ offset 1      │
              │          │ lv 3          │
              │          │ agent "user2" │
              │          └───────┬───────┘
              │                  │
              ├──────────────────┘
              │
      ┌───────┴───────┐
      │ insert "c"    │
      │ offset 3      │
      │ lv 4          │
      │ agent "user1" │
      └───────────────┘
      """.trimIndent(),
      merged.append(Event.createInsert(u1, 2, 3, "c"), merged.version()).toString(),
    )
  }

  @Test
  fun `two histories with no common event stand side by side`() {
    val u1 = agent("user1")
    val u2 = agent("user2")
    val root = Version.root()
    val origin = EventGraph.createGraph()
    val first = origin.append(Event.createInsert(u1, 0, 0, "ab"), root)
    val second = origin.append(Event.createInsert(u2, 0, 0, "cd"), root)
    // Neither run descends from the other, so both sit at depth 0 and neither takes a join.
    assertEquals(
      """
      EventGraph(units=4, runs=2, version=v[1, 3])
      ┌───────────────┐  ┌───────────────┐
      │ insert "ab"   │  │ insert "cd"   │
      │ offset 0      │  │ offset 0      │
      │ lv 0..1       │  │ lv 2..3       │
      │ agent "user1" │  │ agent "user2" │
      └───────────────┘  └───────────────┘
      """.trimIndent(),
      first.mergeFrom(second).toString(),
    )
  }

  @Test
  fun `a new branch draws only the base box of its text`() {
    val branch = DocBranch.createBranch("abc", agent("u"))
    assertEquals(
      """
      EventGraph(units=0, runs=0, version=v[])
      ┌────────────┐
      │ base "abc" │
      └────────────┘
      """.trimIndent(),
      branch.graph().toString(),
    )
  }

  @Test
  fun `a trimmed graph draws its base version in the base box`() {
    val start = DocBranch.createBranch("ab", agent("u")).applyOp(DocumentOp.insertOp(2, "c"))
    val trimmed = start.trimmedTo(start.version()).applyOp(DocumentOp.insertOp(3, "d"))
    assertEquals(
      """
      EventGraph(units=1, runs=1, version=v[0])
      ┌─────────────────────┐
      │ base "abc"          │
      │ version v[("u", 0)] │
      └──────────┬──────────┘
                 │
      ┌──────────┴──────────┐
      │ insert "d"          │
      │ offset 3            │
      │ lv 0                │
      │ agent "u"           │
      └─────────────────────┘
      """.trimIndent(),
      trimmed.graph().toString(),
    )
  }

  @Test
  fun `a trimmed graph with an empty base text still draws its base box`() {
    val start = DocBranch.createBranch("", agent("u"))
      .applyOp(DocumentOp.insertOp(0, "a"))
      .applyOp(DocumentOp.deleteOp(0, 1))
    val trimmed = start.trimmedTo(start.version())
    assertEquals(
      """
      EventGraph(units=0, runs=0, version=v[])
      ┌─────────────────────┐
      │ base ""             │
      │ version v[("u", 1)] │
      └─────────────────────┘
      """.trimIndent(),
      trimmed.graph().toString(),
    )
  }

  @Test
  fun `an agent name neither breaks the base box nor widens it past the bound`() {
    val broken = DocBranch.createBranch("ab", agent("a\nb")).applyOp(DocumentOp.insertOp(2, "c"))
    assertEquals(
      """
      EventGraph(units=0, runs=0, version=v[])
      ┌────────────────────────┐
      │ base "abc"             │
      │ version v[("a\nb", 0)] │
      └────────────────────────┘
      """.trimIndent(),
      broken.trimmedTo(broken.version()).graph().toString(),
    )
    val long = DocBranch.createBranch("ab", agent("n".repeat(300))).applyOp(DocumentOp.insertOp(2, "c"))
    val diagram = long.trimmedTo(long.version()).graph().toString()
    assertTrue(diagram.contains("(300 chars)")) { "the length is not reported: $diagram" }
    assertTrue(diagram.lines().all { it.length < 80 }) { "a line is too wide: $diagram" }
  }

  @Test
  fun `the first run follows the base box`() {
    val branch = DocBranch.createBranch("abc", agent("u")).applyOp(DocumentOp.insertOp(3, "d"))
    assertEquals(
      """
      EventGraph(units=1, runs=1, version=v[0])
      ┌────────────┐
      │ base "abc" │
      └──────┬─────┘
             │
      ┌──────┴─────┐
      │ insert "d" │
      │ offset 3   │
      │ lv 0       │
      │ agent "u"  │
      └────────────┘
      """.trimIndent(),
      branch.graph().toString(),
    )
  }

  @Test
  fun `concurrent runs at the start fork from the base box`() {
    val base = DocBranch.createBranch("ab", agent("u0"))
    val left = base.fork(agent("u1")).applyOp(DocumentOp.insertOp(0, "x"))
    val right = base.fork(agent("u2")).applyOp(DocumentOp.insertOp(2, "y"))
    assertEquals(
      """
      EventGraph(units=2, runs=2, version=v[0, 1])
      ┌────────────┐
      │ base "ab"  │
      └──────┬─────┘
             │
             ├───────────────┐
             │               │
      ┌──────┴─────┐  ┌──────┴─────┐
      │ insert "x" │  │ insert "y" │
      │ offset 0   │  │ offset 2   │
      │ lv 0       │  │ lv 1       │
      │ agent "u1" │  │ agent "u2" │
      └────────────┘  └────────────┘
      """.trimIndent(),
      left.merge(right).graph().toString(),
    )
  }

  @Test
  fun `a run with no parent below the count line joins the count line`() {
    val base = DocBranch.createBranch("ab", agent("u0"))
    var main = base
    // Every edit inserts at the front, so no run extends the one before it.
    repeat(44) {
      main = main.applyOp(DocumentOp.insertOp(0, "x"))
    }
    val late = base.fork(agent("u1")).applyOp(DocumentOp.insertOp(0, "y"))
    val lines = main.merge(late).graph().toString().lines()
    // The header, then the base box with the line under it. Then the first two boxes with the
    // line between them, and the count of what went.
    assertEquals("│ base \"ab\"  │", lines[2])
    assertEquals("... 5 more runs ...", lines[18])
    // The late run follows the base box above the count line, so it joins the count line beside
    // the run whose parent went.
    assertEquals(2, lines[19].count { it == '┴' }) { "a join is missing: ${lines[19]}" }
    assertTrue(lines[22].contains("lv 7") && lines[22].contains("lv 44")) { lines[22] }
  }

  @Test
  fun `a row of many concurrent runs stops and says what it hid`() {
    var graph = EventGraph.createGraph()
    for (i in 0 until 9) {
      graph = graph.append(Event.createInsert(agent("u$i"), 0, 0, "x"), Version.root())
    }
    val diagram = graph.toString()
    // Nine roots are all concurrent, so one row holds them and draws the first six.
    assertEquals(6, diagram.lines()[1].split("┌").size - 1) { "not six boxes: $diagram" }
    assertTrue(diagram.endsWith("... 3 more concurrent runs ...")) { "not bounded: $diagram" }
  }

  @Test
  fun `a long fragment stays short in a box`() {
    val graph = EventGraph.createGraph()
      .append(Event.createInsert(agent("u"), 0, 0, "z".repeat(500)), Version.root())
    val diagram = graph.toString()
    assertTrue(diagram.contains("...")) { "the fragment is not shortened: $diagram" }
    assertTrue(diagram.contains("(500 chars)")) { "the length is not reported: $diagram" }
    // A box of six lines, each one under 80 characters, plus the header.
    assertEquals(7, diagram.lines().size)
    assertTrue(diagram.lines().all { it.length < 80 }) { "a line is too wide: $diagram" }
  }

  @Test
  fun `a long history loses its middle and keeps the newest runs`() {
    val u = agent("u")
    var graph = EventGraph.createGraph()
    // Every append inserts at the front, so no run extends the one before it.
    for (seq in 0 until 45) {
      graph = graph.append(Event.createInsert(u, seq, 0, "x"), graph.version())
    }
    assertEquals(45, graph.runCount())
    val lines = graph.toString().lines()
    // The header, the first two boxes with the line between them, the count of what went,
    // then the newest 38 boxes with their 37 lines.
    assertEquals(1 + (2 * 6 + 1) + 1 + (38 * 6 + 37), lines.size)
    assertEquals("... 5 more runs ...", lines[14])
    // The two ends of the history are both there, and the middle is not.
    assertTrue(lines.any { it.contains("lv 0") }) { "the first run is gone" }
    assertTrue(lines.any { it.contains("lv 44") }) { "the last run is gone" }
    assertFalse(lines.any { it.contains("lv 5 ") }) { "run 5 is in the middle and must go" }
    // The second run has a child that the trim dropped, so its border still shows the line down.
    assertTrue(lines[13].contains('┬')) { "the second box hides its child: ${lines[13]}" }
    // The run under the count line follows one that went, so its border still joins.
    assertTrue(lines[15].contains('┴')) { "the join is missing: ${lines[15]}" }
    // The box holds the kind, the offset, the lv and the agent, one per line.
    assertTrue(lines[18].contains("lv 7")) { "the newest runs do not start at lv 7: ${lines[18]}" }
  }

  /**
   * A run takes the column of its first parent only when no other child of that parent sits
   * deeper, because that child needs the column for its line. Here "c" and "f" both follow "a",
   * and "f" sits deeper, so "c" moves aside and the line of "f" comes from "a" and not from "c".
   */
  @Test
  fun `a merge line leaves only the box of a parent`() {
    // The text is an event of its own, so the diagram draws it as a box.
    val base = DocBranch.createBranch("", agent("u0")).applyOp(DocumentOp.insertOp(0, "base"))
    val a = base.fork(agent("u1")).applyOp(DocumentOp.insertOp(0, "a"))
    val d = base.fork(agent("u2")).applyOp(DocumentOp.insertOp(4, "b")).applyOp(DocumentOp.insertOp(4, "d"))
    val f = a.fork(agent("u3")).merge(d).applyOp(DocumentOp.insertOp(0, "f"))
    val graph = a.applyOp(DocumentOp.insertOp(0, "c")).merge(f).graph()
    assertEquals(
      """
      EventGraph(units=9, runs=6, version=v[5, 8])
      ┌───────────────┐
      │ insert "base" │
      │ offset 0      │
      │ lv 0..3       │
      │ agent "u0"    │
      └───────┬───────┘
              │
              ├─────────────────┐
              │                 │
      ┌───────┴───────┐  ┌──────┴─────┐
      │ insert "a"    │  │ insert "b" │
      │ offset 0      │  │ offset 4   │
      │ lv 4          │  │ lv 6       │
      │ agent "u1"    │  │ agent "u2" │
      └───────┬───────┘  └──────┬─────┘
              │                 │
              ├─────────────────│───────────────┐
              │                 │               │
              │          ┌──────┴─────┐  ┌──────┴─────┐
              │          │ insert "d" │  │ insert "c" │
              │          │ offset 4   │  │ offset 0   │
              │          │ lv 7       │  │ lv 5       │
              │          │ agent "u2" │  │ agent "u1" │
              │          └──────┬─────┘  └────────────┘
              │                 │
              ├─────────────────┘
              │
      ┌───────┴───────┐
      │ insert "f"    │
      │ offset 0      │
      │ lv 8          │
      │ agent "u3"    │
      └───────────────┘
      """.trimIndent(),
      graph.toString(),
    )
  }

  /**
   * "E" follows only "b". Its line to the right passes the column of "b", which it does not join,
   * so the `│` of that column stays whole there and does not become a `┼`.
   */
  @Test
  fun `a line that crosses a column looks different from a join`() {
    assertEquals(
      """
      EventGraph(units=5, runs=5, version=v[3, 4])
      ┌────────────┐  ┌────────────┐  ┌────────────┐
      │ insert "a" │  │ insert "b" │  │ insert "c" │
      │ offset 0   │  │ offset 0   │  │ offset 0   │
      │ lv 0       │  │ lv 1       │  │ lv 2       │
      │ agent "a"  │  │ agent "b"  │  │ agent "c"  │
      └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
             │               │               │
             ├───────────────│───────────────┘
             │               │
      ┌──────┴─────┐  ┌──────┴─────┐
      │ insert "D" │  │ insert "E" │
      │ offset 0   │  │ offset 0   │
      │ lv 3       │  │ lv 4       │
      │ agent "d"  │  │ agent "e"  │
      └────────────┘  └────────────┘
      """.trimIndent(),
      threeRootsAndTwoMerges(Version.of(1)).toString(),
    )
    assertEquals(
      """
      EventGraph(units=5, runs=5, version=v[3, 4])
      ┌────────────┐  ┌────────────┐  ┌────────────┐
      │ insert "a" │  │ insert "b" │  │ insert "c" │
      │ offset 0   │  │ offset 0   │  │ offset 0   │
      │ lv 0       │  │ lv 1       │  │ lv 2       │
      │ agent "a"  │  │ agent "b"  │  │ agent "c"  │
      └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
             │               │               │
             ├───────────────┼───────────────┘
             │               │
      ┌──────┴─────┐  ┌──────┴─────┐
      │ insert "D" │  │ insert "E" │
      │ offset 0   │  │ offset 0   │
      │ lv 3       │  │ lv 4       │
      │ agent "d"  │  │ agent "e"  │
      └────────────┘  └────────────┘
      """.trimIndent(),
      threeRootsAndTwoMerges(Version.of(1, 2)).toString(),
    )
  }

  @Test
  fun `an agent name neither breaks a box nor widens it past the bound`() {
    val broken = EventGraph.createGraph().append(Event.createInsert(agent("a\nb"), 0, 0, "x"), Version.root())
    assertEquals(
      """
      EventGraph(units=1, runs=1, version=v[0])
      ┌──────────────┐
      │ insert "x"   │
      │ offset 0     │
      │ lv 0         │
      │ agent "a\nb" │
      └──────────────┘
      """.trimIndent(),
      broken.toString(),
    )
    val long = EventGraph.createGraph().append(Event.createInsert(agent("n".repeat(300)), 0, 0, "x"), Version.root())
    val diagram = long.toString()
    assertTrue(diagram.contains("(300 chars)")) { "the length is not reported: $diagram" }
    assertTrue(diagram.lines().all { it.length < 80 }) { "a line is too wide: $diagram" }
  }

  /**
   * Three concurrent roots, "D" after "a" and "c", and "E" after [eParents].
   */
  private fun threeRootsAndTwoMerges(eParents: Version): EventGraph {
    var graph = EventGraph.createGraph()
    for (name in listOf("a", "b", "c")) {
      graph = graph.append(Event.createInsert(agent(name), 0, 0, name), Version.root())
    }
    graph = graph.append(Event.createInsert(agent("d"), 0, 0, "D"), Version.of(0, 2))
    return graph.append(Event.createInsert(agent("e"), 0, 0, "E"), eParents)
  }
}
