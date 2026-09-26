// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.mock.Mock
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JPanel

@TestApplication
internal class TextEditorWithPreviewTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  @RunMethodInEdt
  fun undoStateDoesNotContainLayout() {
    val editor = TestTextEditor()
    val preview = TestPreviewEditor()
    val editorWithPreview = TextEditorWithPreview(editor, preview)
    Disposer.register(disposable, editorWithPreview)
    editorWithPreview.component

    editorWithPreview.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR)

    val undoState = editorWithPreview.getState(FileEditorStateLevel.UNDO) as TextEditorWithPreview.MyFileEditorState
    val fullState = editorWithPreview.getState(FileEditorStateLevel.FULL) as TextEditorWithPreview.MyFileEditorState

    assertThat(undoState.splitLayout).isNull()
    assertThat(fullState.splitLayout).isEqualTo(TextEditorWithPreview.Layout.SHOW_EDITOR)

    editorWithPreview.setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW)
    editorWithPreview.setState(undoState)

    assertThat(editorWithPreview.getLayout()).isEqualTo(TextEditorWithPreview.Layout.SHOW_PREVIEW)
  }

  private class TestTextEditor : Mock.MyFileEditor(), TextEditor {
    private val file = MockVirtualFile.file("test.txt")

    override fun getComponent(): JComponent = JPanel()

    override fun getEditor(): Editor = error("Not needed for this test")

    override fun getFile(): VirtualFile = file

    override fun canNavigateTo(navigatable: Navigatable): Boolean = false

    override fun navigateTo(navigatable: Navigatable) {
    }
  }

  private class TestPreviewEditor : Mock.MyFileEditor() {
    override fun getComponent(): JComponent = JPanel()
  }
}
