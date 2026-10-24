// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class PendingMainEditorTest {
  private val project = projectFixture()

  @Test
  fun `publish only after installing the prepared highlighter`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val testProject = project.get()
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance() as EditorFactoryImpl
      val document = factory.createDocument("text")
      val highlighter = EmptyEditorHighlighter()
      highlighter.setText("text")
      var created = 0
      var released = 0
      var configured = false
      factory.addEditorFactoryListener(object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
          if (event.editor.document !== document) return
          assertSame(highlighter, (event.editor as EditorImpl).highlighter)
          assertTrue(configured)
          created++
        }

        override fun editorReleased(event: EditorFactoryEvent) {
          if (event.editor.document === document) released++
        }
      }, disposable)
      val pending = factory.prepareMainEditor(document, testProject, LightVirtualFile("pending.txt", "text"))
      try {
        assertEquals(0, created)
        assertTrue(factory.getEditors(document).isEmpty())
        val editor = pending.complete(highlighter) { configured = true }
        assertFalse(editor.isDisposed)
        assertEquals(1, created)
        assertSame(editor, factory.getEditors(document).single())
      }
      finally {
        pending.close()
      }
      assertEquals(1, released)
      assertTrue(factory.getEditors(document).isEmpty())
    }
  }

  @Test
  fun `discard an unpublished editor without factory events`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val testProject = project.get()
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance() as EditorFactoryImpl
      val document = factory.createDocument("text")
      var events = 0
      factory.addEditorFactoryListener(object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
          if (event.editor.document === document) events++
        }

        override fun editorReleased(event: EditorFactoryEvent) {
          if (event.editor.document === document) events++
        }
      }, disposable)
      val pending = factory.prepareMainEditor(document, testProject, LightVirtualFile("discard.txt", "text"))
      val editor = pendingEditor(pending)
      pending.close()
      pending.close()
      assertTrue(editor.isDisposed)
      assertEquals(0, events)
      assertTrue(factory.getEditors(document).isEmpty())
      assertThrows(IllegalStateException::class.java) { pending.complete(EmptyEditorHighlighter()) {} }
    }
  }

  @Test
  fun `release the editor when configuration fails before publication`(): Unit = timeoutRunBlocking {
    val testProject = project.get()
    withContext(Dispatchers.EDT) {
      val factory = EditorFactory.getInstance() as EditorFactoryImpl
      val document = factory.createDocument("text")
      val pending = factory.prepareMainEditor(document, testProject, LightVirtualFile("failed.txt", "text"))
      val editor = pendingEditor(pending)
      try {
        assertThrows(IllegalArgumentException::class.java) {
          pending.complete(EmptyEditorHighlighter()) { throw IllegalArgumentException("Test failure") }
        }
      }
      finally {
        pending.close()
      }
      assertTrue(editor.isDisposed)
      assertTrue(factory.getEditors(document).isEmpty())
    }
  }
}

private fun pendingEditor(pending: EditorFactoryImpl.PendingMainEditor): EditorImpl {
  val field = pending.javaClass.getDeclaredField("editor")
  field.isAccessible = true
  return field.get(pending) as EditorImpl
}
