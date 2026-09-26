// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.util.Disposer
import kotlin.test.assertFailsWith

class CustomWrapModelTest : AbstractEditorTest() {

  override fun setUp() {
    super.setUp()
    setUpCustomWrapSupport()
  }

  private val customWrapModel: CustomWrapModel
    get() = editor.customWrapModel

  fun testAddAndRemoveWraps() {
    initText("Hello World")

    val wrap = customWrapModel.runBatchMutation { addWrap(5) }!!

    assertSize(1, customWrapModel.getWraps())
    assertSize(1, customWrapModel.getWrapsAtOffset(5))
    assertTrue(customWrapModel.hasWraps())
    assertEquals(5, wrap.offset)
    editor.customWrapModel.runBatchMutation { removeWrap(wrap) }
    assertSize(0, customWrapModel.getWraps())
    assertFalse(customWrapModel.hasWraps())
  }

  fun testGetWrapsSortedByOffsetAndPriority() {
    initText("Hello World Test String For Sorting")

    customWrapModel.runBatchMutation {
      addWrap(15, 2, 2)
      addWrap(15, 4, 1)
      addWrap(5)
      addWrap(25)
      addWrap(10)
      addWrap(20)
    }
    
    val wraps = customWrapModel.getWraps()
    
    assertEquals(6, wraps.size)
    
    val offsetsAndPriorities = wraps.map { it.offset to it.priority }
    assertEquals(listOf(5 to 0, 10 to 0, 15 to 1, 15 to 2, 20 to 0, 25 to 0), offsetsAndPriorities)
  }

  fun testListenerReceivesSingleAddAndRemoveCallbacks() {
    initText("abcdef")

    val events = mutableListOf<String>()
    val disposable = Disposer.newDisposable()
    customWrapModel.addListener(object : CustomWrapModel.Listener {
      override fun customWrapAdded(wrap: CustomWrap) {
        events += "add:${wrap.offset}"
      }

      override fun customWrapRemoved(wrap: CustomWrap) {
        events += "remove:${wrap.offset}"
      }
    }, disposable)

    val wrap = customWrapModel.runBatchMutation { addWrap(3) }!!
    customWrapModel.runBatchMutation { removeWrap(wrap) }
    Disposer.dispose(disposable)
    assertNotNull(customWrapModel.runBatchMutation { addWrap(2) })
    assertEquals(listOf("add:3", "remove:3"), events)
  }

  fun testExplicitRemoveNotifiesRemovedExactlyOnce() {
    initText("abcdef")

    var removeCount = 0
    var removedOffset = -1
    customWrapModel.addListener(object : CustomWrapModel.Listener {
      override fun customWrapRemoved(wrap: CustomWrap) {
        removeCount++
        removedOffset = wrap.offset
      }
    }, getTestRootDisposable())

    val wrap = customWrapModel.runBatchMutation { addWrap(3) }!!
    customWrapModel.runBatchMutation { removeWrap(wrap) }

    assertEquals(1, removeCount)
    assertEquals(3, removedOffset)
  }

  fun testRemoveWrapReturnsFalseForAlreadyInvalidatedWrap() {
    initText("abc\ndef")

    val (survivingWrap, invalidatedWrap) = customWrapModel.runBatchMutation {
      addWrap(2)!! to addWrap(5)!!
    }

    runWriteCommand {
      editor.document.deleteString(4, 5)
    }

    assertEquals(listOf(2), customWrapModel.getWraps().map { it.offset })

    val (survivingWrapRemoved, invalidatedWrapRemoved) = customWrapModel.runBatchMutation {
      removeWrap(survivingWrap) to removeWrap(invalidatedWrap)
    }

    assertTrue(survivingWrapRemoved)
    assertFalse(invalidatedWrapRemoved)
    assertTrue(customWrapModel.getWraps().isEmpty())
  }

  fun testCustomWrapInvalidatedByDocumentChangeNotifiesRemovedExactlyOnce() {
    initText("abc\ndef")

    var removeCount = 0
    var removedOffset = -1
    customWrapModel.addListener(object : CustomWrapModel.Listener {
      override fun customWrapRemoved(wrap: CustomWrap) {
        removeCount++
        removedOffset = wrap.offset
      }
    }, getTestRootDisposable())

    assertNotNull(customWrapModel.runBatchMutation { addWrap(5) })

    runWriteCommand {
      editor.document.deleteString(4, 5)
    }

    assertTrue(customWrapModel.getWraps().isEmpty())
    assertEquals(1, removeCount)
    assertEquals(4, removedOffset)
  }

  fun testAddWrapRejectsLineBoundaryOffsets() {
    initText("ab\ncd")

    val events = mutableListOf<String>()
    addRecordingListener(events)

    customWrapModel.runBatchMutation {
      assertNull(addWrap(2))
      assertNull(addWrap(3))
    }

    assertFalse(customWrapModel.hasWraps())
    assertTrue(customWrapModel.getWraps().isEmpty())
    assertTrue(events.isEmpty())
  }

  fun testAddWrapRejectsOffsetInsideSurrogatePair() {
    initText("a${SURROGATE_PAIR}b")

    assertNull(customWrapModel.runBatchMutation { addWrap(2) })
    assertFalse(customWrapModel.hasWraps())
  }

  fun testDeleteRemovesWrapThatBecomesLineEnd() {
    initText("abc\ndef")

    val events = mutableListOf<String>()
    addRecordingListener(events)

    assertNotNull(customWrapModel.runBatchMutation { addWrap(2) })

    runWriteCommand {
      editor.document.deleteString(2, 3)
    }

    assertTrue(customWrapModel.getWraps().isEmpty())
    assertEquals(listOf("add:2", "remove:2"), events)
  }

  fun testDeleteRemovesWrapThatBecomesLineStart() {
    initText("abc\ndef")

    val events = mutableListOf<String>()
    addRecordingListener(events)

    assertNotNull(customWrapModel.runBatchMutation { addWrap(5) })

    runWriteCommand {
      editor.document.deleteString(4, 5)
    }

    assertTrue(customWrapModel.getWraps().isEmpty())
    assertEquals(listOf("add:5", "remove:4"), events)
  }

  fun testMoveRemovesWrapThatBecomesLineStart() {
    initText("abc\ndefghi")

    val events = mutableListOf<String>()
    addRecordingListener(events)

    assertNotNull(customWrapModel.runBatchMutation { addWrap(6) })

    runWriteCommand {
      (editor.document as DocumentEx).moveText(6, 8, 4)
    }

    assertEquals("abc\nfgdehi", editor.document.text)
    assertTrue(customWrapModel.getWraps().isEmpty())
    assertEquals(listOf("add:6", "remove:4"), events)
  }

  fun testMutatorOperationsOutsideBatchMutationFailAndDoNotModifyModel() {
    initText("0123456789")
    val mutator = editor.customWrapModel.runBatchMutation {
      addWrap(4)
      this // mutator leaks out of scope
    }

    assertFailsWith<IllegalArgumentException> { mutator.addWrap(5) }
    assertEquals(listOf(4), editor.customWrapModel.getWraps().map { it.offset })

    (editor as EditorImpl).validateState()
  }

  private fun addRecordingListener(events: MutableList<String>) {
    customWrapModel.addListener(object : CustomWrapModel.Listener {
      override fun customWrapAdded(wrap: CustomWrap) {
        events += "add:${wrap.offset}"
      }

      override fun customWrapRemoved(wrap: CustomWrap) {
        events += "remove:${wrap.offset}"
      }
    }, getTestRootDisposable())
  }
}
