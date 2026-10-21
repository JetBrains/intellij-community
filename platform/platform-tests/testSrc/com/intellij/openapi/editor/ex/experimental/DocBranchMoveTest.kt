// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Tests text moves in [DocumentBranch]: how a local move op records, and the move offsets in [DocMerge.ops].
 */
@TestApplication
class DocBranchMoveTest {

  // ------------------------------------------------------------------------------- local move ops

  @Test
  fun `a move op that does not name the moved text applies and merges as a plain edit`() {
    // The source of the copied "01" is at 0, not at 1.
    val wrongSource = BASE.fork(agent("a")).applyOp(DocumentOp.insertOp(4, "01", 1))
    assertEquals("012301456789", wrongSource.string())
    assertEquals(listOf(insertOp(4, "01")), opsOfMerge(BASE, wrongSource))
    // The copy of the deleted "01" is at 4, not at 5.
    val copied = BASE.fork(agent("a")).applyOp(DocumentOp.insertOp(4, "01", 0))
    val wrongCopy = copied.applyOp(DocumentOp.deleteOp(0, 2, 5))
    assertEquals("2301456789", wrongCopy.string())
    assertEquals(listOf(insertOp(4, "01"), deleteOp(0, 2)), opsOfMerge(BASE, wrongCopy))
  }

  @Test
  fun `a move op outside the text applies and merges as a plain edit`() {
    val pastEndInsert = BASE.fork(agent("a")).applyOp(DocumentOp.insertOp(10, "01", 11))
    assertEquals(listOf(insertOp(10, "01")), opsOfMerge(BASE, pastEndInsert))
    val negative = BASE.fork(agent("a")).applyOp(DocumentOp.deleteOp(0, 2, -1))
    assertEquals(listOf(deleteOp(0, 2)), opsOfMerge(BASE, negative))
    val pastEndDelete = BASE.fork(agent("a")).applyOp(DocumentOp.deleteOp(0, 2, 9))
    assertEquals(listOf(deleteOp(0, 2)), opsOfMerge(BASE, pastEndDelete))
  }

  @Test
  fun `a move op that overlaps its own text applies and merges as a plain edit`() {
    // The text at the move offset is the moved text, but it overlaps the text that the op changes.
    val base = DocumentBranch.createBranch("aaaa", agent("base"))
    val insert = base.fork(agent("a")).applyOp(DocumentOp.insertOp(1, "aa", 2))
    assertEquals("aaaaaa", insert.string())
    assertEquals(listOf(insertOp(1, "aa")), opsOfMerge(base, insert))
    val delete = base.fork(agent("a")).applyOp(DocumentOp.deleteOp(0, 2, 1))
    assertEquals("aa", delete.string())
    assertEquals(listOf(deleteOp(0, 2)), opsOfMerge(base, delete))
  }

  // ------------------------------------------------------------------------------------ merge ops

  @Test
  fun `a fast-forward gives back every move of a linear history`() {
    val ops = listOf(
      DocumentOp.insertOp(4, "01", 0),
      DocumentOp.deleteOp(0, 2, 4),
      // A keystroke at the end of the copy.
      DocumentOp.insertOp(4, "x"),
      DocumentOp.insertOp(0, "78", 10),
      DocumentOp.deleteOp(10, 2, 0),
    )
    val start = BASE.fork(agent("a"))
    val descendant = ops.fold(start) { branch, op -> branch.applyOp(op) }
    assertEquals("782301x4569", descendant.string())
    assertEquals(ops, opsOfMerge(BASE, descendant))
  }

  @Test
  fun `two disjoint moves arrive with move offsets in every merge`() {
    val a = BASE.fork(agent("a")).moveText(0, 2, 4)
    val b = BASE.fork(agent("b")).moveText(6, 8, 10)
    val moveOfA = listOf(DocumentOp.insertOp(4, "01", 0), DocumentOp.deleteOp(0, 2, 4))
    val moveOfB = listOf(DocumentOp.insertOp(10, "67", 6), DocumentOp.deleteOp(6, 2, 10))
    val ab = a.merge(b)
    val ba = b.merge(a)
    assertEquals(moveOfA + moveOfB, opsOfMerge(BASE, ab))
    assertEquals(moveOfB + moveOfA, opsOfMerge(BASE, ba))
    assertEquals(moveOfB, opsOfMerge(a, b))
    assertEquals(moveOfA, opsOfMerge(b, a))
  }

  @Test
  fun `a copy next to the source of another move keeps both moves`() {
    // The copy of B lands right before the source of A.
    val a = BASE.fork(agent("a")).moveText(4, 6, 10)
    val b = BASE.fork(agent("b")).moveText(6, 8, 4)
    val ab = a.merge(b)
    val ba = b.merge(a)
    assertEquals(
      listOf(
        DocumentOp.insertOp(10, "45", 4),
        DocumentOp.deleteOp(4, 2, 10),
        DocumentOp.insertOp(4, "67", 6),
        DocumentOp.deleteOp(6, 2, 4),
      ),
      opsOfMerge(BASE, ab),
    )
    assertEquals(
      listOf(
        DocumentOp.insertOp(4, "67", 8),
        DocumentOp.deleteOp(8, 2, 4),
        DocumentOp.insertOp(10, "45", 6),
        DocumentOp.deleteOp(6, 2, 10),
      ),
      opsOfMerge(BASE, ba),
    )
  }

  @Test
  fun `of two moves into one text the first one walked keeps its move offsets`() {
    // The copy of A lands between "6" and "7", inside the source of B.
    val a = BASE.fork(agent("a")).moveText(0, 2, 7)
    val b = BASE.fork(agent("b")).moveText(6, 8, 10)
    val ab = a.merge(b)
    val ba = b.merge(a)
    assertEquals(
      listOf(
        DocumentOp.insertOp(7, "01", 0),
        DocumentOp.deleteOp(0, 2, 7),
        DocumentOp.insertOp(10, "67"),
        DocumentOp.deleteOp(4, 1),
        DocumentOp.deleteOp(6, 1),
      ),
      opsOfMerge(BASE, ab),
    )
    // Walked first, the move of B finds its source intact. The copy of A lands where that source was.
    assertEquals(
      listOf(
        DocumentOp.insertOp(10, "67", 6),
        DocumentOp.deleteOp(6, 2, 10),
        DocumentOp.insertOp(6, "01", 0),
        DocumentOp.deleteOp(0, 2, 6),
      ),
      opsOfMerge(BASE, ba),
    )
  }

  @Test
  fun `of two moves of one source the first one walked keeps its move offsets`() {
    val a = BASE.fork(agent("a")).moveText(0, 2, 4)
    val b = BASE.fork(agent("b")).moveText(0, 2, 10)
    val ab = a.merge(b)
    val ba = b.merge(a)
    // Both copies stay, as in any merge of two moves.
    assertEquals("230145678901", ab.string())
    assertEquals(
      listOf(
        DocumentOp.insertOp(4, "01", 0),
        DocumentOp.deleteOp(0, 2, 4),
        DocumentOp.insertOp(10, "01"),
      ),
      opsOfMerge(BASE, ab),
    )
    assertEquals(
      listOf(
        DocumentOp.insertOp(10, "01", 0),
        DocumentOp.deleteOp(0, 2, 10),
        DocumentOp.insertOp(2, "01"),
      ),
      opsOfMerge(BASE, ba),
    )
  }

  @Test
  fun `of two moves of overlapping sources the first one walked keeps its move offsets`() {
    val a = BASE.fork(agent("a")).moveText(0, 3, 6)
    val b = BASE.fork(agent("b")).moveText(2, 4, 8)
    val ab = a.merge(b)
    val ba = b.merge(a)
    assertEquals(
      listOf(
        DocumentOp.insertOp(6, "012", 0),
        DocumentOp.deleteOp(0, 3, 6),
        DocumentOp.insertOp(8, "23"),
        DocumentOp.deleteOp(0, 1),
      ),
      opsOfMerge(BASE, ab),
    )
    assertEquals(
      listOf(
        DocumentOp.insertOp(8, "23", 2),
        DocumentOp.deleteOp(2, 2, 8),
        DocumentOp.insertOp(4, "012"),
        DocumentOp.deleteOp(0, 2),
      ),
      opsOfMerge(BASE, ba),
    )
  }

  @Test
  fun `a concurrent edit inside the moved text makes the move plain`() {
    val a = BASE.fork(agent("a")).moveText(0, 2, 4)
    val move = listOf(DocumentOp.insertOp(4, "01", 0), DocumentOp.deleteOp(0, 2, 4))
    // Walked after the move, the typed "x" stays where the source was.
    val typed = BASE.fork(agent("b")).applyOp(insertOp(1, "x"))
    val moveFirst = a.merge(typed)
    val typedFirst = typed.merge(a)
    assertEquals(move + insertOp(0, "x"), opsOfMerge(BASE, moveFirst))
    assertEquals(
      listOf(
        insertOp(1, "x"),
        insertOp(5, "01"),
        deleteOp(0, 1),
        deleteOp(1, 1),
      ),
      opsOfMerge(BASE, typedFirst),
    )
    // Walked after the move, the delete of "1" finds nothing to delete, so the "1" stays in the copy.
    val deleted = BASE.fork(agent("b")).applyOp(deleteOp(1, 1))
    val moveBeforeDelete = a.merge(deleted)
    val deleteBeforeMove = deleted.merge(a)
    assertEquals(move, opsOfMerge(BASE, moveBeforeDelete))
    assertEquals(
      listOf(
        deleteOp(1, 1),
        insertOp(3, "01"),
        deleteOp(0, 1),
      ),
      opsOfMerge(BASE, deleteBeforeMove),
    )
  }

  @Test
  fun `a move of text from several runs arrives as one pair`() {
    val a = BASE.fork(agent("a"))
      .applyOp(insertOp(1, "ab"))
      .moveText(0, 4, 8)
    val b = BASE.fork(agent("b")).applyOp(insertOp(10, "!"))
    assertEquals("23450ab16789", a.string())
    assertEquals(
      listOf(
        DocumentOp.insertOp(1, "ab"),
        DocumentOp.insertOp(8, "0ab1", 0),
        DocumentOp.deleteOp(0, 4, 8),
      ),
      opsOfMerge(b, a),
    )
  }

  @Test
  fun `an edit typed and deleted inside the moved text keeps the move`() {
    val move = listOf(DocumentOp.insertOp(4, "01", 0), DocumentOp.deleteOp(0, 2, 4))
    val own = BASE.fork(agent("a"))
      .applyOp(insertOp(1, "x"))
      .applyOp(deleteOp(1, 1))
      .moveText(0, 2, 4)
    assertEquals(move, opsOfMerge(BASE, own))
    val a = BASE.fork(agent("a")).moveText(0, 2, 4)
    val other = BASE.fork(agent("b"))
      .applyOp(insertOp(1, "y"))
      .applyOp(deleteOp(1, 1))
    val merged = other.merge(a)
    assertEquals(move, opsOfMerge(BASE, merged))
    assertEquals(move, opsOfMerge(other, a))
  }

  @Test
  fun `a half of a move without its other half arrives as a plain op`() {
    val base = DocumentBranch.createBranch("0123", agent("base"))
    val moveInsert = DocumentOp.insertOp(4, "01", 0)
    val moveDelete = DocumentOp.deleteOp(0, 2, 4)
    val copied = base.fork(agent("a")).applyOp(moveInsert)
    val moved = copied.applyOp(moveDelete)
    assertEquals(listOf(insertOp(4, "01")), opsOfMerge(base, copied))
    assertEquals(listOf(deleteOp(0, 2)), opsOfMerge(copied, moved))
    assertEquals(listOf(moveInsert, moveDelete), opsOfMerge(base, moved))
  }

  @Test
  fun `random moves of a linear history arrive with their move offsets`() {
    repeat(ROUNDS) { round ->
      fuzzRound(20261008L, round) { random ->
        var descendant = BASE.fork(agent("a"))
        val recordedMoves = ArrayList<DocumentOp.Text>()
        repeat(STEPS) {
          val move = randomMoveOps(random, descendant.text().chars())
          if (move.isNotEmpty() && random.nextBoolean()) {
            recordedMoves.addAll(move)
            descendant = move.fold(descendant) { branch, op -> branch.applyOp(op) }
          } else {
            descendant = descendant.applyOp(randomEdit(random, descendant.length()))
          }
        }
        val ops = opsOfMerge(BASE, descendant)
        assertMovePairs(BASE.text(), ops)
        assertEquals(recordedMoves, ops.filter { it.moveOffset() != it.offset() })
      }
    }
  }

  // ----------------------------------------------------------------------------------- a document

  @Test
  fun `merge ops move the markers as local moves do`() {
    val a = BASE.fork(agent("a")).moveText(0, 2, 4)
    val b = BASE.fork(agent("b")).moveText(6, 8, 10)
    val ab = a.merge(b)
    val ops = opsOfMerge(BASE, ab)

    val local = DocumentImpl(BASE.string(), true)
    val localMarkers = markersOf(local)
    val localEvents = recordEvents(local)
    local.moveText(0, 2, 4)
    local.moveText(6, 8, 10)

    val merged = DocumentImpl(BASE.string(), true)
    val mergedMarkers = markersOf(merged)
    val mergedEvents = recordEvents(merged)
    for (op in ops) {
      applyWithMoveOffset(merged, op)
    }

    assertEquals("2301458967", merged.text)
    assertEquals(localEvents, mergedEvents)
    assertEquals(listOf("[2, 4)", "[8, 10)", "[3, 3)"), mergedMarkers.map { rangeOf(it) })
    assertEquals(localMarkers.map { rangeOf(it) }, mergedMarkers.map { rangeOf(it) })
  }

  private fun opsOfMerge(receiver: DocumentBranch, other: DocumentBranch): List<DocumentOp.Text> {
    return receiver.mergeWithOps(other).ops()
  }

  /**
   * A marker on "01", a marker on "67", and a point marker between "0" and "1".
   */
  private fun markersOf(document: DocumentImpl): List<RangeMarker> {
    return listOf(
      document.createRangeMarker(0, 2),
      document.createRangeMarker(6, 8),
      document.createRangeMarker(1, 1),
    )
  }

  private fun rangeOf(marker: RangeMarker): String {
    if (!marker.isValid) {
      return "invalid"
    }
    return "[${marker.startOffset}, ${marker.endOffset})"
  }

  private fun recordEvents(document: DocumentImpl): List<String> {
    val events = ArrayList<String>()
    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        events.add("${event.offset}: -${event.oldLength} +'${event.newFragment}', move=${event.moveOffset}")
      }
    })
    return events
  }

  /**
   * Applies [op] to [document] with its move offset, as an editor applies a merge.
   */
  private fun applyWithMoveOffset(document: DocumentImpl, op: DocumentOp.Text) {
    val start = op.offset()
    val stamp = document.modificationStamp + 1
    when (op) {
      is DocumentOp.Insert -> {
        document.replaceString(start, start, op.moveOffset(), op.fragment(), stamp)
      }
      is DocumentOp.Delete -> {
        val end = start + op.length()
        document.replaceString(start, end, op.moveOffset(), "", stamp)
      }
    }
  }

  private fun randomEdit(random: Random, length: Int): DocumentOp.Text {
    if (length == 0 || random.nextBoolean()) {
      return insertOp(random.nextInt(length + 1), "xy")
    }
    return deleteOp(random.nextInt(length), 1)
  }

  private companion object {
    const val ROUNDS = 500
    const val STEPS = 20

    val BASE: DocumentBranch = DocumentBranch.createBranch("0123456789", agent("base"))
  }
}
