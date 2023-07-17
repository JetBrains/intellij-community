package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.openapi.editor.Editor")
interface Editor {
  fun getDocument(): Document
}

@Remote("com.intellij.openapi.editor.Document")
interface Document {
  fun getText(): String
}

@Remote("com.intellij.openapi.fileEditor.FileEditor")
interface FileEditor

@Remote("com.intellij.openapi.fileEditor.FileEditorManager")
interface FileEditorManager {
  fun openFile(file: VirtualFile, focusEditor: Boolean, searchForOpen: Boolean): Array<FileEditor?>
}
