// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Per-client version of file editor manager
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface ClientFileEditorManager {
  fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider?
  fun getSelectedEditor(): FileEditor?
  fun getSelectedEditorWithProvider(): FileEditorWithProvider?
  fun getSelectedEditors(): List<FileEditor>
  fun getSelectedTextEditor(): Editor?
  fun getSelectedFile(): VirtualFile?
  fun getSelectedFiles(): List<VirtualFile>

  fun setSelectedEditor(file: VirtualFile, providerId: String)

  fun getEditorsWithProviders(file: VirtualFile): List<FileEditorWithProvider>
  fun getEditors(file: VirtualFile): List<FileEditor>

  fun openFile(file: VirtualFile, forceCreate: Boolean): List<FileEditorWithProvider>
  fun closeFile(file: VirtualFile, closeAllCopies: Boolean)
  fun isFileOpen(file: VirtualFile): Boolean

  fun createComposite(file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>): EditorComposite?

  fun getComposite(file: VirtualFile): EditorComposite?
  fun getComposite(editor: FileEditor): EditorComposite?
  fun getAllComposites(file: VirtualFile): List<EditorComposite>
  fun getAllComposites(): List<EditorComposite>
  fun removeComposite(composite: EditorComposite)

  fun getAllFiles(): List<VirtualFile>
  fun getAllEditors(): List<FileEditor>
}