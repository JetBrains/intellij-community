// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A floating toolbar with no action group for the selection does not show. Plain text has no group of the code toolbar.
 */
@TestApplication
internal class FloatingToolbarWithoutActionGroupTest {
  private companion object {
    val project = projectFixture()
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("file.txt", "some selected text")
  }

  @Test
  fun `the code toolbar cannot show when the language at the caret has no group`(): Unit = timeoutRunBlocking {
    // The dispose of the toolbar cancels its scope, so the test does not wait for it.
    val toolbarScope = childScope("FloatingToolbarWithoutActionGroupTest")
    withContext(Dispatchers.EDT) {
      val editor = createEditorWithSelection()
      val toolbar = CodeFloatingToolbar(editor, toolbarScope)
      try {
        assertFalse(toolbar.canBeShownAtCurrentSelection())
      }
      finally {
        Disposer.dispose(toolbar)
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  @Test
  fun `show calls back when the toolbar has no action group`(): Unit = timeoutRunBlocking {
    // The dispose of the toolbar cancels its scope, so the test does not wait for it.
    val toolbarScope = childScope("FloatingToolbarWithoutActionGroupTest")
    withContext(Dispatchers.EDT) {
      val editor = createEditorWithSelection()
      val toolbar = object : FloatingToolbar(editor, toolbarScope) {
        override fun createActionGroup(): ActionGroup? = null
      }
      try {
        assertTrue(toolbar.canBeShownAtCurrentSelection(), "show() gets past the check and asks for the group")
        val calledBack = CompletableDeferred<Unit>()
        toolbar.show { calledBack.complete(Unit) }
        calledBack.await()
        assertFalse(toolbar.isShown())
      }
      finally {
        Disposer.dispose(toolbar)
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  private fun createEditorWithSelection(): Editor {
    val document = PsiDocumentManager.getInstance(project.get()).getDocument(file.get())!!
    val editor = EditorFactory.getInstance().createEditor(document, project.get())
    editor.selectionModel.setSelection(5, 13)
    return editor
  }
}
