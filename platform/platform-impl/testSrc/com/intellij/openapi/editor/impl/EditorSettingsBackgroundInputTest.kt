// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The tab size of an editor through the background branch of [BackgroundStateInput], which production uses.
 */
@TestApplication
internal class EditorSettingsBackgroundInputTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("file.txt", "abc")
  }

  /**
   * A code style change must reach the state, and then the view. `EditorView` keeps the tab size in its snapshot, so
   * only the editor refresh after the state change brings the new value there.
   */
  @Test
  fun testCodeStyleChangeReachesTheView(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    BackgroundStateInput.computeInBackgroundInTests(disposable)
    val project = project.get()
    val virtualFile = file.get().virtualFile
    val editor = withContext(Dispatchers.UiWithModelAccess) {
      writeIntentReadAction {
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(virtualFile))
        EditorFactory.getInstance().createEditor(document, project, virtualFile, false) as EditorImpl
      }
    }
    try {
      val settings = editor.settings as SettingsImpl
      val state = settings.getState()
      // The first read starts the first computation.
      settings.getTabSize(project)
      state.awaitInputsForTests()
      val changed = settings.getTabSize(project) + 3

      val manager = CodeStyleSettingsManager.getInstance(project)
      val temporary = manager.cloneSettings(CodeStyle.getSettings(project))
      temporary.indentOptions.TAB_SIZE = changed
      CodeStyle.setTemporarySettings(project, temporary)
      try {
        withContext(Dispatchers.UiWithModelAccess) {
          writeIntentReadAction {
            manager.fireCodeStyleSettingsChanged()
          }
        }
        state.awaitInputsForTests()

        assertEquals(changed, state.tabSize)
        assertEquals(changed, settings.getTabSize(project))
        val viewTabSize = withContext(Dispatchers.UiWithModelAccess) {
          editor.view.tabSize
        }
        assertEquals(changed, viewTabSize)
      }
      finally {
        CodeStyle.dropTemporarySettings(project)
      }
    }
    finally {
      withContext(Dispatchers.UiWithModelAccess) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }
}
