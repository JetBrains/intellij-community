package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.remoteDev.GuestNavigationService
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.openapi.editor.Editor")
interface Editor {
  fun getDocument(): Document
  fun getProject(): Project
  fun getCaretModel(): CaretModel
  fun logicalPositionToXY(position: LogicalPosition): Point
  fun getVirtualFile(): VirtualFile
  fun getLineHeight(): Int
  fun offsetToVisualPosition(offset: Int): VisualPosition
  fun visualPositionToXY(visible: VisualPosition): Point
  fun getInlayModel(): InlayModel
  fun getColorsScheme(): EditorColorsScheme
}
@Remote("com.intellij.openapi.editor.VisualPosition")
interface VisualPosition {
  fun getLine(): Int
  fun getColumn(): Int
}
@Remote("com.intellij.openapi.editor.Document")
interface Document {
  fun getText(): String
  fun setText(text: String)
}

@Remote("com.intellij.openapi.editor.CaretModel")
interface CaretModel {
  fun moveToLogicalPosition(position: LogicalPosition)
  fun moveToVisualPosition(pos: VisualPosition)
  fun getLogicalPosition(): LogicalPosition
  fun moveToOffset(offset: Int)
}

@Remote("com.intellij.openapi.editor.InlayModel")
interface InlayModel {
  fun getInlineElementsInRange(startOffset: Int, endOffset: Int): List<Inlay>
}

@Remote("com.intellij.openapi.editor.Inlay")
interface Inlay {
  fun getRenderer(): EditorCustomElementRenderer
  fun getOffset(): Int
}

@Remote("com.intellij.openapi.editor.EditorCustomElementRenderer")
interface EditorCustomElementRenderer{
  fun getText(): String?
}

@Remote("com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayRenderer")
interface DeclarativeInlayRenderer{
  fun getPresentationList(): InlayPresentationList
}

@Remote("com.intellij.codeInsight.hints.declarative.impl.InlayPresentationList")
interface InlayPresentationList{
  fun getEntries(): Array<TextInlayPresentationEntry>
}


@Remote("com.intellij.codeInsight.hints.declarative.impl.TextInlayPresentationEntry")
interface TextInlayPresentationEntry{
  fun getText(): String
}

@Remote("com.intellij.openapi.editor.LogicalPosition")
interface LogicalPosition {

  fun getLine(): Int

  fun getColumn(): Int
}

fun Driver.logicalPosition(line: Int, column: Int): LogicalPosition {
  return new(LogicalPosition::class, line, column)
}

@Remote("com.intellij.openapi.fileEditor.FileEditor")
interface FileEditor

@Remote("com.intellij.openapi.fileEditor.FileEditorManager")
interface FileEditorManager {
  fun openFile(file: VirtualFile, focusEditor: Boolean, searchForOpen: Boolean): Array<FileEditor>
  fun getSelectedTextEditor(): Editor?
}

@Remote("com.intellij.openapi.editor.colors.EditorColorsScheme")
interface EditorColorsScheme {
  fun getEditorFontSize(): Int
}

fun Driver.openEditor(file: VirtualFile, project: Project? = null): Array<FileEditor> {
  return withContext(OnDispatcher.EDT) {
    service<FileEditorManager>(project ?: singleProject()).openFile(file, true, false)
  }
}

fun Driver.openFile(relativePath: String, project: Project = singleProject(), waitForCodeAnalysis: Boolean = true) = withContext {
  val openedFile = if (!isRemoteIdeMode) {
    val fileToOpen = findFile(relativePath = relativePath, project = project)
    if (fileToOpen == null) {
      throw IllegalArgumentException("Fail to find file $relativePath")
    }
    openEditor(file = fileToOpen)
    fileToOpen
  }
  else {
    val service = service(GuestNavigationService::class, project)
    withContext(OnDispatcher.EDT) {
      service.navigateViaBackend(relativePath, 0)
      waitFor(message = "File is opened: $relativePath", timeout = 30.seconds,
              getter = {
                service<FileEditorManager>(project).getSelectedTextEditor()?.getVirtualFile()
              },
              checker = { virtualFile ->
                virtualFile != null &&
                virtualFile.getPath().contains(relativePath)
              })!!
    }
  }
  if (waitForCodeAnalysis) {
    waitForCodeAnalysis(file = openedFile)
  }
}