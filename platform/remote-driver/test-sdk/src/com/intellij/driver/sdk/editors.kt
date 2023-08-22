package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher

@Remote("com.intellij.openapi.editor.Editor")
interface Editor {
  fun getDocument(): Document
  fun getProject(): Project
}

@Remote("com.intellij.openapi.editor.Document")
interface Document {
  fun getText(): String
  fun setText(text: String)
}

@Remote("com.intellij.openapi.fileEditor.FileEditor")
interface FileEditor

@Remote("com.intellij.openapi.fileEditor.FileEditorManager")
interface FileEditorManager {
  fun openFile(file: VirtualFile, focusEditor: Boolean, searchForOpen: Boolean): Array<FileEditor>
}

fun Driver.openEditor(project: Project, file: VirtualFile): Array<FileEditor> {
  return withContext(OnDispatcher.EDT) {
    service<FileEditorManager>(project).openFile(file, true, false)
  }
}