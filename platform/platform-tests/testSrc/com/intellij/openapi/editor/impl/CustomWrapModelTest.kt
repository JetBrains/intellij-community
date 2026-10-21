// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.EditorModelFixture.Companion.SURROGATE_PAIR
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

@TestApplication
class CustomWrapModelTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @BeforeEach
  fun setUp() {
    fixture.setUpCustomWrapSupport()
  }

  private val customWrapModel: CustomWrapModel
    get() = fixture.editor.customWrapModel

  @Test
  fun testAddAndRemoveWraps(): Unit = fixture.runTest {
    initText("Hello World")

    val wrap = customWrapModel.runBatchMutation { addWrap(5) }!!

    assertThat(customWrapModel.getWraps()).hasSize(1)
    assertThat(customWrapModel.getWrapsAtOffset(5)).hasSize(1)
    assertTrue(customWrapModel.hasWraps())
    assertEquals(5, wrap.offset)
    editor.customWrapModel.runBatchMutation { removeWrap(wrap) }
    assertThat(customWrapModel.getWraps()).isEmpty()
    assertFalse(customWrapModel.hasWraps())
  }

  @Test
  fun testGetWrapsSortedByOffsetAndPriority(): Unit = fixture.runTest {
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

  @Test
  fun testListenerReceivesSingleAddAndRemoveCallbacks(): Unit = fixture.runTest {
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

  @Test
  fun testExplicitRemoveNotifiesRemovedExactlyOnce(): Unit = fixture.runTest {
    initText("abcdef")

    var removeCount = 0
    var removedOffset = -1
    customWrapModel.addListener(object : CustomWrapModel.Listener {
      override fun customWrapRemoved(wrap: CustomWrap) {
        removeCount++
        removedOffset = wrap.offset
      }
    }, testRootDisposable)

    val wrap = customWrapModel.runBatchMutation { addWrap(3) }!!
    customWrapModel.runBatchMutation { removeWrap(wrap) }

    assertEquals(1, removeCount)
    assertEquals(3, removedOffset)
  }

  @Test
  fun testRemoveWrapReturnsFalseForAlreadyInvalidatedWrap(): Unit = fixture.runTest {
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

  @Test
  fun testCustomWrapInvalidatedByDocumentChangeNotifiesRemovedExactlyOnce(): Unit = fixture.runTest {
    initText("abc\ndef")

    var removeCount = 0
    var removedOffset = -1
    customWrapModel.addListener(object : CustomWrapModel.Listener {
      override fun customWrapRemoved(wrap: CustomWrap) {
        removeCount++
        removedOffset = wrap.offset
      }
    }, testRootDisposable)

    assertNotNull(customWrapModel.runBatchMutation { addWrap(5) })

    runWriteCommand {
      editor.document.deleteString(4, 5)
    }

    assertTrue(customWrapModel.getWraps().isEmpty())
    assertEquals(1, removeCount)
    assertEquals(4, removedOffset)
  }

  @Test
  fun testAddWrapRejectsLineBoundaryOffsets(): Unit = fixture.runTest {
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

  @Test
  fun testAddWrapRejectsOffsetInsideSurrogatePair(): Unit = fixture.runTest {
    initText("a${SURROGATE_PAIR}b")

    assertNull(customWrapModel.runBatchMutation { addWrap(2) })
    assertFalse(customWrapModel.hasWraps())
  }

  @Test
  fun testDeleteRemovesWrapThatBecomesLineEnd(): Unit = fixture.runTest {
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

  @Test
  fun testDeleteRemovesWrapThatBecomesLineStart(): Unit = fixture.runTest {
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

  @Test
  fun testMoveRemovesWrapThatBecomesLineStart(): Unit = fixture.runTest {
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

  @Test
  fun testMutatorOperationsOutsideBatchMutationFailAndDoNotModifyModel(): Unit = fixture.runTest {
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
    }, fixture.testRootDisposable)
  }
}
