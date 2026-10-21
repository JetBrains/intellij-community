// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.FileIndentOptionsProvider
import com.intellij.testFramework.ExtensionTestUtil
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
   * The settings start the first computation when they are built, and no read of the value is needed.
   *
   * A test provider gives the file a tab size that differs from the project setting, so only the computation can bring
   * it. `createEditor(document, project)` sets no highlighter after the build, so no reinit starts a computation either.
   */
  @Test
  fun testTheComputationStartsWhenTheEditorIsBuilt(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    BackgroundStateInput.computeInBackgroundInTests(disposable)
    val testProject = project.get()
    val testFile = file.get().virtualFile
    val fileTabSize = CodeStyle.getSettings(testProject).indentOptions.TAB_SIZE + 5
    val provider = object : FileIndentOptionsProvider() {
      override fun getIndentOptions(project: Project, settings: CodeStyleSettings, file: VirtualFile): IndentOptions? {
        if (file != testFile) {
          return null
        }
        val options = IndentOptions()
        options.TAB_SIZE = fileTabSize
        return options
      }
    }
    ExtensionTestUtil.maskExtensions(FileIndentOptionsProvider.EP_NAME, listOf(provider), disposable)

    val editor = createEditor(testProject, testFile)
    try {
      val state = (editor.settings as SettingsImpl).getState()
      state.awaitInputsForTests()

      assertEquals(fileTabSize, state.tabSize)
      val viewTabSize = withContext(Dispatchers.UiWithModelAccess) {
        editor.view.tabSize
      }
      assertEquals(fileTabSize, viewTabSize)
    }
    finally {
      releaseEditor(editor)
    }
  }

  /**
   * A code style change must reach the state, and then the view. `EditorView` keeps the tab size in its snapshot, so
   * only the editor refresh after the state change brings the new value there.
   */
  @Test
  fun testCodeStyleChangeReachesTheView(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    BackgroundStateInput.computeInBackgroundInTests(disposable)
    val testProject = project.get()
    val editor = createEditor(testProject, file.get().virtualFile)
    try {
      val settings = editor.settings as SettingsImpl
      val state = settings.getState()
      // The settings start the first computation when they are built.
      state.awaitInputsForTests()
      val changed = settings.getTabSize(testProject) + 3

      val manager = CodeStyleSettingsManager.getInstance(testProject)
      val temporary = manager.cloneSettings(CodeStyle.getSettings(testProject))
      temporary.indentOptions.TAB_SIZE = changed
      CodeStyle.setTemporarySettings(testProject, temporary)
      try {
        withContext(Dispatchers.UiWithModelAccess) {
          writeIntentReadAction {
            manager.fireCodeStyleSettingsChanged()
          }
        }
        state.awaitInputsForTests()

        assertEquals(changed, state.tabSize)
        assertEquals(changed, settings.getTabSize(testProject))
        val viewTabSize = withContext(Dispatchers.UiWithModelAccess) {
          editor.view.tabSize
        }
        assertEquals(changed, viewTabSize)
      }
      finally {
        CodeStyle.dropTemporarySettings(testProject)
      }
    }
    finally {
      releaseEditor(editor)
    }
  }

  /**
   * Builds an editor with `createEditor(document, project)`, which sets no highlighter after the build.
   */
  private suspend fun createEditor(project: Project, file: VirtualFile): EditorImpl {
    return withContext(Dispatchers.UiWithModelAccess) {
      writeIntentReadAction {
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
        EditorFactory.getInstance().createEditor(document, project) as EditorImpl
      }
    }
  }

  private suspend fun releaseEditor(editor: EditorImpl) {
    withContext(Dispatchers.UiWithModelAccess) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }
}
