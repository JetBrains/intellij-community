// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretActionListener
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import java.awt.Color
import kotlin.test.assertFailsWith

class CaretModelImplTest : AbstractEditorTest() {
  fun `test primary caret exposes editor model position and selection contracts`() {
    initText("ab\ncd")

    val caret = primaryCaret
    caret.moveToOffset(1)

    assertSame(editor, caret.editor)
    assertSame(caretModel, caret.caretModel)
    assertTrue(caret.isValid)
    assertTrue(caret.isUpToDate)
    assertTrue(caretModel.supportsMultipleCarets())
    assertEquals(1, caretModel.caretCount)
    assertSame(caret, caretModel.primaryCaret)
    assertSame(caret, caretModel.currentCaret)
    assertEquals(listOf(caret), caretModel.allCarets)
    assertEquals(LogicalPosition(0, 1), caret.logicalPosition)
    assertEquals(VisualPosition(0, 1), caret.visualPosition)
    assertEquals(1, caret.offset)
    assertEquals(0, caret.visualLineStart)
    assertEquals(3, caret.visualLineEnd)
    assertFalse(caret.hasSelection())
    assertEquals(TextRange.create(1, 1), caret.selectionRange)
    assertEquals(1, caret.selectionStart)
    assertEquals(1, caret.selectionEnd)
    assertEquals(VisualPosition(0, 1), caret.selectionStartPosition)
    assertEquals(VisualPosition(0, 1), caret.selectionEndPosition)
    assertEquals(1, caret.leadSelectionOffset)
    assertEquals(VisualPosition(0, 1), caret.leadSelectionPosition)
    assertNull(caret.selectedText)
    assertFalse(caret.isAtRtlLocation)
    assertFalse(caret.isAtBidiRunBoundary)
  }

  fun `test caret row text attributes are cached and reinitialized`() {
    initText("abc")

    val attributes = caretModel.textAttributes
    assertSame(attributes, caretModel.textAttributes)

    caretModelImpl.reinitSettings()

    val reinitializedAttributes = caretModel.textAttributes
    assertTrue(attributes !== reinitializedAttributes)
  }

  fun `test max caret count is lower bounded and prevents adding more carets`() {
    Registry.get("editor.max.caret.count").setValue(0, rootDisposable)
    initText("abc")

    assertEquals(1, caretModel.maxCaretCount)
    assertNull(caretModel.addCaret(VisualPosition(0, 1), true))
    assertEquals(1, caretModel.caretCount)
  }

  fun `test add caret by visual and logical position respects primary duplicate selection and sorting contracts`() {
    initText("abc\ndef\nghi")

    primaryCaret.setSelection(1, 3)
    assertNull(caretModel.addCaret(LogicalPosition(0, 2), true))

    primaryCaret.removeSelection()
    val laterCaret = caretModel.addCaret(VisualPosition(2, 1), false)!!
    val middleCaret = caretModel.addCaret(LogicalPosition(1, 2), true)!!

    assertSame(middleCaret, caretModel.primaryCaret)
    assertEquals(3, caretModel.caretCount)
    assertEquals(
      listOf(VisualPosition(0, 0), VisualPosition(1, 2), VisualPosition(2, 1)),
      caretModel.allCarets.map { it.visualPosition },
    )
    assertSame(laterCaret, caretModel.getCaretAt(VisualPosition(2, 1)))
    assertNull(caretModel.getCaretAt(VisualPosition(2, 2)))
    assertNull(caretModel.addCaret(VisualPosition(1, 2), true))
  }

  fun `test removing carets updates validity primary caret and listener registration`() {
    initText("abc\ndef")

    data class EventPosition(val line: Int, val column: Int)
    data class EventLog(val type: String, val oldPosition: EventPosition?, val newPosition: EventPosition?)

    fun position(position: LogicalPosition) = EventPosition(position.line, position.column)

    val events = mutableListOf<EventLog>()
    val listener = object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        events += EventLog("move", position(event.oldPosition), position(event.newPosition))
      }

      override fun caretAdded(event: CaretEvent) {
        events += EventLog("add", null, position(event.caret.logicalPosition))
      }

      override fun caretRemoved(event: CaretEvent) {
        events += EventLog("remove", position(event.caret.logicalPosition), null)
      }
    }
    caretModel.addCaretListener(listener)

    primaryCaret.moveToOffset(1)
    val secondaryCaret = caretModel.addCaret(VisualPosition(1, 1), true)!!

    assertTrue(caretModel.removeCaret(secondaryCaret))
    assertFalse(secondaryCaret.isValid)
    assertSame(primaryCaret, caretModel.primaryCaret)
    assertFalse(caretModel.removeCaret(secondaryCaret))
    assertFalse(caretModel.removeCaret(primaryCaret))
    assertEquals(1, caretModel.caretCount)

    caretModel.removeCaretListener(listener)
    primaryCaret.moveToOffset(2)

    assertEquals(
      listOf(
        EventLog("move", EventPosition(0, 0), EventPosition(0, 1)),
        EventLog("add", null, EventPosition(1, 1)),
        EventLog("remove", EventPosition(1, 1), null),
      ),
      events,
    )
  }

  fun `test removing foreign caret does not affect either caret model`() {
    initText("abc")

    val editorFactory = EditorFactory.getInstance()
    val foreignEditor = editorFactory.createEditor(editorFactory.createDocument("xy"))
    try {
      val foreignCaret = foreignEditor.caretModel.primaryCaret

      assertFalse(caretModel.removeCaret(foreignCaret))

      assertEquals(1, caretModel.caretCount)
      assertTrue(primaryCaret.isValid)
      assertEquals(1, foreignEditor.caretModel.caretCount)
      assertTrue(foreignCaret.isValid)
    }
    finally {
      editorFactory.releaseEditor(foreignEditor)
    }
  }

  fun `test remove secondary carets leaves primary caret valid`() {
    initText("abc\ndef\nghi")

    val firstSecondaryCaret = caretModel.addCaret(VisualPosition(0, 2), false)!!
    val primary = caretModel.addCaret(VisualPosition(2, 1), true)!!

    caretModel.removeSecondaryCarets()

    assertEquals(1, caretModel.caretCount)
    assertSame(primary, caretModel.primaryCaret)
    assertTrue(primary.isValid)
    assertFalse(firstSecondaryCaret.isValid)
    assertEquals(listOf(VisualPosition(2, 1)), caretModel.allCarets.map { it.visualPosition })
  }

  fun `test set carets and selections creates trims preserves and restores caret state`() {
    Registry.get("editor.max.caret.count").setValue(2, rootDisposable)
    initText("abc\ndef\nghi")

    caretModel.setCaretsAndSelections(
      listOf(
        CaretState(LogicalPosition(2, 1), LogicalPosition(2, 0), LogicalPosition(2, 2)),
        CaretState(LogicalPosition(0, 2), LogicalPosition(0, 1), LogicalPosition(0, 3)),
        CaretState(LogicalPosition(1, 1), null, null),
      ),
      false,
    )

    assertEquals(2, caretModel.caretCount)
    assertEquals(LogicalPosition(0, 2), caretModel.primaryCaret.logicalPosition)
    assertEquals(
      listOf(VisualPosition(0, 2), VisualPosition(2, 1)),
      caretModel.allCarets.map { it.visualPosition },
    )
    assertEquals(listOf("bc", "gh"), caretModel.allCarets.map { it.selectedText })

    val savedStates = caretModel.caretsAndSelections
    caretModel.setCaretsAndSelections(listOf(CaretState(LogicalPosition(1, 0), null, null)), false)
    caretModel.setCaretsAndSelections(savedStates, false)

    assertEquals(2, caretModel.caretCount)
    assertEquals(LogicalPosition(0, 2), caretModel.primaryCaret.logicalPosition)
    assertEquals(listOf("bc", "gh"), caretModel.allCarets.map { it.selectedText })

    val firstSavedPosition = savedStates.first().caretPosition
    caretModel.setCaretsAndSelections(listOf(CaretState(null, null, null)), false)
    assertEquals(1, caretModel.caretCount)
    assertEquals(firstSavedPosition, caretModel.primaryCaret.logicalPosition)

    assertFailsWith<IllegalArgumentException> {
      caretModel.setCaretsAndSelections(emptyList<CaretState>(), false)
    }
  }

  fun `test carets and selections restore visual position around inline inlays`() {
    initText("ab")

    primaryCaret.moveToOffset(1)
    val inlay = addInlay(1)
    try {
      val savedVisualPosition = primaryCaret.visualPosition
      assertEquals(VisualPosition(0, 2), savedVisualPosition)

      val savedState = caretModel.caretsAndSelections.single()
      assertEquals(LogicalPosition(0, 1), savedState.caretPosition)

      primaryCaret.moveToOffset(0)
      caretModel.setCaretsAndSelections(listOf(savedState), false)

      assertEquals(1, primaryCaret.offset)
      assertEquals(savedVisualPosition, primaryCaret.visualPosition)
    }
    finally {
      Disposer.dispose(inlay)
    }
  }

  fun `test run for each caret exposes current caret order listeners disposal and recursion guard`() {
    initText("abc\ndef\nghi")
    caretModel.setCaretsAndSelections(
      listOf(
        CaretState(LogicalPosition(2, 1), null, null),
        CaretState(LogicalPosition(0, 2), null, null),
        CaretState(LogicalPosition(1, 0), null, null),
      ),
      false,
    )

    val actionEvents = mutableListOf<String>()
    val disposable = Disposer.newDisposable()
    Disposer.register(rootDisposable, disposable)
    caretModel.addCaretActionListener(object : CaretActionListener {
      override fun beforeAllCaretsAction() {
        actionEvents += "before"
      }

      override fun afterAllCaretsAction() {
        actionEvents += "after"
      }
    }, disposable)

    val forwardPositions = mutableListOf<LogicalPosition>()
    caretModel.runForEachCaret { caret ->
      assertSame(caret, caretModel.currentCaret)
      assertTrue(caretModelImpl.isIteratingOverCarets)
      forwardPositions += caret.logicalPosition
    }

    val reversePositions = mutableListOf<LogicalPosition>()
    caretModel.runForEachCaret({ caret -> reversePositions += caret.logicalPosition }, true)

    assertEquals(
      listOf(LogicalPosition(0, 2), LogicalPosition(1, 0), LogicalPosition(2, 1)),
      forwardPositions,
    )
    assertEquals(forwardPositions.asReversed(), reversePositions)
    assertEquals(listOf("before", "after", "before", "after"), actionEvents)
    assertFalse(caretModelImpl.isIteratingOverCarets)

    assertFailsWith<IllegalStateException> {
      caretModel.runForEachCaret {
        caretModel.runForEachCaret {}
      }
    }
    val eventsBeforeDisposal = actionEvents.toList()

    Disposer.dispose(disposable)
    caretModel.runForEachCaret {}
    assertEquals(eventsBeforeDisposal, actionEvents)
  }

  fun `test run for each caret iterates over initial snapshot when carets are added`() {
    initText("a\nb\nc")

    assertNotNull(caretModel.addCaret(VisualPosition(1, 0), false))

    val visitedPositions = mutableListOf<LogicalPosition>()
    caretModel.runForEachCaret { caret ->
      visitedPositions += caret.logicalPosition
      if (visitedPositions.size == 1) {
        assertNotNull(caretModel.addCaret(VisualPosition(2, 0), false))
      }
    }

    assertEquals(
      listOf(LogicalPosition(0, 0), LogicalPosition(1, 0)),
      visitedPositions,
    )
    assertEquals(
      listOf(VisualPosition(0, 0), VisualPosition(1, 0), VisualPosition(2, 0)),
      caretModel.allCarets.map { it.visualPosition },
    )
  }

  fun `test batch caret operation postpones merging until operation is complete`() {
    initText("abc\ndef")
    val originalCaret = primaryCaret
    val secondaryCaret = caretModel.addCaret(VisualPosition(1, 1), true)!!

    caretModel.runBatchCaretOperation {
      secondaryCaret.moveToOffset(originalCaret.offset)
      assertEquals(2, caretModel.caretCount)
      assertTrue(secondaryCaret.isValid)
    }

    assertEquals(1, caretModel.caretCount)
    assertFalse(secondaryCaret.isValid)
    assertEquals(0, caretModel.primaryCaret.offset)
  }

  fun `test move to offset distinguishes soft wrap sides`() {
    initText("abcdefghi")
    configureSoftWraps(4, false)

    primaryCaret.moveToOffset(4)
    assertEquals(LogicalPosition(0, 4), primaryCaret.logicalPosition)
    assertEquals(VisualPosition(1, 1), primaryCaret.visualPosition)

    primaryCaret.moveToOffset(4, true)
    assertEquals(LogicalPosition(0, 4), primaryCaret.logicalPosition)
    assertEquals(VisualPosition(0, 4), primaryCaret.visualPosition)
  }

  fun `test move to logical and visual positions clamp and support virtual space`() {
    initText("abc\nx")

    primaryCaret.moveToVisualPosition(VisualPosition(10, 50))
    assertEquals(LogicalPosition(1, 1), primaryCaret.logicalPosition)
    assertEquals(VisualPosition(1, 1), primaryCaret.visualPosition)
    assertEquals(5, primaryCaret.offset)

    primaryCaret.moveToLogicalPosition(LogicalPosition(10, 50))
    assertEquals(LogicalPosition(1, 1), primaryCaret.logicalPosition)
    assertEquals(5, primaryCaret.offset)

    editor.settings.isVirtualSpace = true
    primaryCaret.moveToLogicalPosition(LogicalPosition(0, 6))
    assertEquals(LogicalPosition(0, 6), primaryCaret.logicalPosition)
    assertEquals(3, primaryCaret.offset)
    assertTrue(caretImpl.isInVirtualSpace)

    primaryCaret.moveToVisualPosition(VisualPosition(0, 8))
    assertEquals(VisualPosition(0, 8), primaryCaret.visualPosition)
    assertTrue(caretImpl.isInVirtualSpace)
  }

  fun `test move caret relatively moves across lines extends selection and clears selection`() {
    initText("ab\ncdef")

    primaryCaret.moveToOffset(1)
    primaryCaret.moveCaretRelatively(1, 0, false, false)
    assertEquals(LogicalPosition(0, 2).leanForward(true), primaryCaret.logicalPosition)
    primaryCaret.moveCaretRelatively(1, 0, false, false)
    assertEquals(LogicalPosition(1, 0), primaryCaret.logicalPosition)

    primaryCaret.moveToOffset(5)
    primaryCaret.moveCaretRelatively(0, -10, true, false)
    assertEquals(0, primaryCaret.offset)
    assertEquals(0, primaryCaret.selectionStart)
    assertEquals(5, primaryCaret.selectionEnd)

    primaryCaret.moveCaretRelatively(1, 0, false, false)
    assertFalse(primaryCaret.hasSelection())
    assertEquals(1, primaryCaret.offset)
  }

  fun `test moving into collapsed fold expands it and positions caret inside`() {
    initText("abcdef")
    val foldRegion = addCollapsedFoldRegion(1, 5, "...")

    primaryCaret.moveToOffset(3)

    assertTrue(foldRegion.isExpanded)
    assertEquals(3, primaryCaret.offset)
    assertEquals(LogicalPosition(0, 3), primaryCaret.logicalPosition)
  }

  fun `test selection overloads expose offsets visual positions ranges lead and selected text`() {
    initText("abcdef")

    primaryCaret.moveToOffset(4)
    primaryCaret.setSelection(1, 4)

    assertTrue(primaryCaret.hasSelection())
    assertEquals(1, primaryCaret.selectionStart)
    assertEquals(4, primaryCaret.selectionEnd)
    assertEquals(TextRange.create(1, 4), primaryCaret.selectionRange)
    assertEquals(VisualPosition(0, 1), primaryCaret.selectionStartPosition)
    assertEquals(VisualPosition(0, 4), primaryCaret.selectionEndPosition)
    assertEquals(1, primaryCaret.leadSelectionOffset)
    assertEquals(VisualPosition(0, 1), primaryCaret.leadSelectionPosition)
    assertEquals("bcd", primaryCaret.selectedText)

    primaryCaret.setSelection(4, 2, false)
    assertEquals(2, primaryCaret.selectionStart)
    assertEquals(4, primaryCaret.selectionEnd)
    assertEquals("cd", primaryCaret.selectedText)

    primaryCaret.setSelection(0, VisualPosition(0, 3), 3)
    assertEquals(0, primaryCaret.selectionStart)
    assertEquals(3, primaryCaret.selectionEnd)
    assertEquals(VisualPosition(0, 0), primaryCaret.selectionStartPosition)
    assertEquals(VisualPosition(0, 3), primaryCaret.selectionEndPosition)

    primaryCaret.setSelection(VisualPosition(0, 5), 5, VisualPosition(0, 1), 1, false)
    assertEquals(1, primaryCaret.selectionStart)
    assertEquals(5, primaryCaret.selectionEnd)
    assertEquals(VisualPosition(0, 1), primaryCaret.selectionStartPosition)
    assertEquals(VisualPosition(0, 5), primaryCaret.selectionEndPosition)
    assertEquals(5, primaryCaret.leadSelectionOffset)
    assertEquals(VisualPosition(0, 5), primaryCaret.leadSelectionPosition)

    primaryCaret.setSelection(1, 1)
    assertFalse(primaryCaret.hasSelection())
    assertEquals(TextRange.create(4, 4), primaryCaret.selectionRange)
  }

  fun `test selection expands to collapsed fold boundaries`() {
    initText("abcdef")
    val foldRegion = addCollapsedFoldRegion(1, 5, "...")

    primaryCaret.setSelection(2, 3)

    assertFalse(foldRegion.isExpanded)
    assertEquals(1, primaryCaret.selectionStart)
    assertEquals(5, primaryCaret.selectionEnd)
    assertEquals("bcde", primaryCaret.selectedText)
  }

  fun `test visual aware selection can represent virtual selection in column mode`() {
    initText("abc")
    (editor as EditorEx).setColumnMode(true)

    primaryCaret.moveToOffset(3)
    primaryCaret.setSelection(VisualPosition(0, 3), 3, VisualPosition(0, 6), 3, false)

    assertTrue(primaryCaret.hasSelection())
    assertEquals(3, primaryCaret.selectionStart)
    assertEquals(3, primaryCaret.selectionEnd)
    assertEquals(TextRange.create(3, 3), primaryCaret.selectionRange)
    assertEquals(VisualPosition(0, 3), primaryCaret.selectionStartPosition)
    assertEquals(VisualPosition(0, 6), primaryCaret.selectionEndPosition)
    assertEquals("   ", primaryCaret.selectedText)
  }

  fun `test remove selection respects sticky selection mode`() {
    initText("abcdef")

    primaryCaret.moveToOffset(4)
    primaryCaret.setSelection(1, 4)
    primaryCaret.removeSelection()
    assertFalse(primaryCaret.hasSelection())

    primaryCaret.setSelection(1, 4)
    (editor as EditorEx).setStickySelection(true)
    primaryCaret.removeSelection()
    assertTrue(primaryCaret.hasSelection())
  }

  fun `test select line and word at caret update current selection`() {
    initText("one two\nthree")

    primaryCaret.moveToOffset(5)
    primaryCaret.selectWordAtCaret(false)
    assertEquals("two", primaryCaret.selectedText)

    primaryCaret.moveToOffset(1)
    primaryCaret.selectLineAtCaret()
    assertEquals("one two\n", primaryCaret.selectedText)
  }

  fun `test select word at caret honors camel humps parameter`() {
    val editorSettings = EditorSettingsExternalizable.getInstance()
    val oldCamelWords = editorSettings.isCamelWords
    try {
      editorSettings.setCamelWords(true)
      initText("fooBar")

      primaryCaret.moveToOffset(4)
      primaryCaret.selectWordAtCaret(true)
      assertEquals("Bar", primaryCaret.selectedText)

      primaryCaret.removeSelection()
      primaryCaret.selectWordAtCaret(false)
      assertEquals("fooBar", primaryCaret.selectedText)
    }
    finally {
      editorSettings.setCamelWords(oldCamelWords)
    }
  }

  fun `test clone copies caret column and selection and returns null outside document or at occupied target`() {
    initText("abc\ndef\nghi")

    val original = primaryCaret
    original.moveToLogicalPosition(LogicalPosition(1, 1))
    original.setSelection(
      editor.logicalToVisualPosition(LogicalPosition(1, 0)),
      editor.logicalPositionToOffset(LogicalPosition(1, 0)),
      editor.logicalToVisualPosition(LogicalPosition(1, 2)),
      editor.logicalPositionToOffset(LogicalPosition(1, 2)),
      false,
    )

    val above = original.clone(true)!!
    val below = original.clone(false)!!

    assertEquals(3, caretModel.caretCount)
    assertEquals(LogicalPosition(0, 1), above.logicalPosition)
    assertEquals("ab", above.selectedText)
    assertEquals(LogicalPosition(2, 1), below.logicalPosition)
    assertEquals("gh", below.selectedText)
    assertNull(above.clone(true))
    assertNull(above.clone(false))
  }

  fun `test clone returns null when maximum caret count is reached`() {
    Registry.get("editor.max.caret.count").setValue(1, rootDisposable)
    initText("a\nb")

    assertNull(primaryCaret.clone(false))
    assertEquals(1, caretModel.caretCount)
  }

  fun `test visual attributes can be overridden and reset to default`() {
    initText("abc")

    assertSame(CaretVisualAttributes.getDefault(), primaryCaret.visualAttributes)

    val customAttributes = CaretVisualAttributes(Color.RED, CaretVisualAttributes.Weight.HEAVY, CaretVisualAttributes.Shape.BOX, 0.5f)
    primaryCaret.visualAttributes = customAttributes
    assertSame(customAttributes, primaryCaret.visualAttributes)

    primaryCaret.visualAttributes = CaretVisualAttributes.getDefault()
    assertSame(CaretVisualAttributes.getDefault(), primaryCaret.visualAttributes)
  }

  fun `test document updates shift caret without explicit movement events`() {
    initText("ab<caret>cd")

    var movementEvents = 0
    caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        movementEvents++
      }
    }, rootDisposable)

    runWriteCommand {
      editor.document.insertString(0, "x")
    }

    assertEquals(3, primaryCaret.offset)
    assertEquals(LogicalPosition(0, 3), primaryCaret.logicalPosition)
    assertEquals(0, movementEvents)
    assertTrue(primaryCaret.isUpToDate)
  }

  fun `test caret never stays inside surrogate pair`() {
    initText("a${SURROGATE_PAIR}b")

    primaryCaret.moveToOffset(2)
    primaryCaret.setSelection(0, 2)

    assertFalse(com.intellij.util.DocumentUtil.isInsideSurrogatePair(editor.document, primaryCaret.offset))
    assertFalse(com.intellij.util.DocumentUtil.isInsideSurrogatePair(editor.document, primaryCaret.selectionEnd))
  }

  fun `test inline inlay add and remove recalculates visual position at caret offset`() {
    initText("ab")

    primaryCaret.moveToOffset(1)
    assertEquals(VisualPosition(0, 1), primaryCaret.visualPosition)

    val inlay = addInlay(1)
    assertEquals(1, primaryCaret.offset)
    assertEquals(VisualPosition(0, 2), primaryCaret.visualPosition)

    Disposer.dispose(inlay)
    assertEquals(1, primaryCaret.offset)
    assertEquals(VisualPosition(0, 1), primaryCaret.visualPosition)
  }

  fun `test explicit visual position update keeps logical caret and refreshes soft wrap visual position`() {
    initText("abcdefghi")
    primaryCaret.moveToOffset(editor.document.textLength)

    val logicalPosition = primaryCaret.logicalPosition
    configureSoftWraps(4, false)
    caretModelImpl.updateVisualPosition()

    assertEquals(logicalPosition, primaryCaret.logicalPosition)
    assertEquals(VisualPosition(2, 3), primaryCaret.visualPosition)
  }

  private val caretModel: CaretModel
    get() = editor.caretModel

  private val caretModelImpl: CaretModelImpl
    get() = caretModel as CaretModelImpl

  private val primaryCaret: Caret
    get() = caretModel.primaryCaret

  private val caretImpl: CaretImpl
    get() = primaryCaret as CaretImpl

  private val rootDisposable: Disposable
    get() = getTestRootDisposable()
}
