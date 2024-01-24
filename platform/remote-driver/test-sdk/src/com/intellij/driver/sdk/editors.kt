package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher

@Remote("com.intellij.openapi.editor.Editor")
interface Editor {
  fun getDocument(): Document
  fun getProject(): Project
  fun getCaretModel(): CaretModel
}

@Remote("com.intellij.openapi.editor.Document")
interface Document {
  fun getText(): String
  fun setText(text: String)
}

@Remote("com.intellij.openapi.editor.CaretModel")
interface CaretModel {
  fun moveToLogicalPosition(position: LogicalPosition)
}

@Remote("com.intellij.openapi.editor.LogicalPosition")
interface LogicalPosition

fun Driver.logicalPosition(line: Int, column: Int): LogicalPosition {
  return new(LogicalPosition::class, line, column)
}

@Remote("com.intellij.openapi.fileEditor.FileEditor")
interface FileEditor

@Remote("com.intellij.openapi.fileEditor.FileEditorManager")
interface FileEditorManager {
  fun openFile(file: VirtualFile, focusEditor: Boolean, searchForOpen: Boolean): Array<FileEditor>
}

fun Driver.openEditor(project: Project? = null, file: VirtualFile): Array<FileEditor> {
  return withContext(OnDispatcher.EDT) {
    service<FileEditorManager>(project ?: singleProject()).openFile(file, true, false)
  }
}

fun Driver.openFile(relativePath: String) {
  val fileToOpen = findFile(relativePath = relativePath)
  if (fileToOpen == null) {
    throw IllegalArgumentException("Fail to find file $relativePath")
  }
  openEditor(file = fileToOpen)
  waitForCodeAnalysis(file = fileToOpen)
}