package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.remoteDev.GuestNavigationService
import com.intellij.driver.sdk.ui.remote.ColorRef
import java.awt.Point
import java.awt.Rectangle
import java.nio.file.Path
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
  fun offsetToLogicalPosition(offset: Int): LogicalPosition
  fun visualPositionToXY(visualPosition: VisualPosition): Point
  fun offsetToXY(offset: Int): Point
  fun getInlayModel(): InlayModel
  fun getColorsScheme(): EditorColorsScheme
  fun logicalPositionToOffset(logicalPosition: LogicalPosition): Int
  fun getSelectionModel(): SelectionModel
  fun getSoftWrapModel(): SoftWrapModel
  fun visualLineToY(visualLine: Int): Int
  fun getMarkupModel(): MarkupModel
  fun getScrollingModel(): ScrollingModel
}

@Remote("com.intellij.openapi.editor.markup.MarkupModel")
interface MarkupModel {
  fun getAllHighlighters(): Array<RangeHighlighter>
}

@Remote("com.intellij.openapi.editor.markup.RangeHighlighter")
interface RangeHighlighter {
  fun getStartOffset(): Int
  fun getEndOffset(): Int
  fun getTextAttributes(): TextAttributes?
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
  fun getLineNumber(offset: Int): Int
  fun getLineStartOffset(line: Int): Int
  fun getLineEndOffset(line: Int): Int
  fun getLineCount(): Int
}

@Remote("com.intellij.openapi.editor.CaretModel")
interface CaretModel {
  fun moveToLogicalPosition(position: LogicalPosition)
  fun moveToVisualPosition(pos: VisualPosition)
  fun getLogicalPosition(): LogicalPosition
  fun getAllCarets(): List<Caret>
  fun moveToOffset(offset: Int)
  fun getOffset(): Int
  fun getCurrentCaret(): Caret
}
@Remote("com.intellij.openapi.editor.Caret")
interface Caret {
  fun getLogicalPosition(): LogicalPosition
  fun getVisualAttributes(): CaretVisualAttributes
}

@Remote("com.intellij.openapi.editor.CaretVisualAttributes")
interface CaretVisualAttributes {
  fun getColor(): ColorRef?
}

@Remote("com.intellij.openapi.editor.ScrollingModel")
interface ScrollingModel {
  fun scrollToCaret(type: ScrollType)
  fun scrollTo(pos: LogicalPosition, scrollType: ScrollType)
}

@Remote("com.intellij.openapi.editor.ScrollType")
interface ScrollType {
  fun valueOf(name: String): ScrollType
}

@Remote("com.intellij.openapi.editor.InlayModel")
interface InlayModel {
  fun getInlineElementsInRange(startOffset: Int, endOffset: Int): List<Inlay>
  fun getBlockElementsInRange(startOffset: Int, endOffset: Int): List<Inlay>
  fun getAfterLineEndElementsForLogicalLine(logicalLine: Int): List<Inlay>
}

@Remote("com.intellij.openapi.editor.Inlay")
interface Inlay {
  fun getRenderer(): EditorCustomElementRenderer
  fun getOffset(): Int
  fun getBounds(): Rectangle
}

@Remote("com.intellij.openapi.editor.SoftWrapModel")
interface SoftWrapModel {
  fun isSoftWrappingEnabled(): Boolean
}

@Remote("com.intellij.openapi.editor.EditorCustomElementRenderer")
interface EditorCustomElementRenderer {
  fun getText(): String?
}

@Remote("com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRenderer")
interface DeclarativeInlayRenderer {
  fun getPresentationList(): InlayPresentationList
}

@Remote("com.intellij.codeInsight.daemon.impl.HintRenderer")
interface HintRenderer {
  fun getText(): String
}

@Remote("com.intellij.codeInsight.inline.completion.render.InlineCompletionLineRenderer")
interface InlineCompletionLineRenderer {
  fun getBlocks(): List<InlineCompletionRenderTextBlock>
}

@Remote("com.intellij.codeInsight.inline.completion.render.InlineCompletionRenderTextBlock")
interface InlineCompletionRenderTextBlock {
  val text: String
}

@Remote("com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList")
interface InlayPresentationList {
  fun getEntries(): Array<TextInlayPresentationEntry>
}

@Remote("com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry")
interface TextInlayPresentationEntry {
  fun getText(): String
}

@Remote("com.intellij.openapi.editor.LogicalPosition")
interface LogicalPosition {

  fun getLine(): Int

  fun getColumn(): Int
}

fun Driver.logicalPosition(line: Int, column: Int, rdTarget: RdTarget = RdTarget.DEFAULT): LogicalPosition {
  return new(LogicalPosition::class, line, column, rdTarget = rdTarget)
}

@Remote("com.intellij.openapi.fileEditor.FileEditor")
interface FileEditor {
  fun getFile(): VirtualFile
}

@Remote("com.intellij.openapi.fileEditor.FileEditorManager")
interface FileEditorManager {
  fun openFile(file: VirtualFile, focusEditor: Boolean, searchForOpen: Boolean): Array<FileEditor>
  fun getSelectedTextEditor(): Editor?
  fun setSelectedEditor(editor: FileEditor)
  fun getAllEditors(): Array<FileEditor>
  fun getCurrentFile(): VirtualFile
}

@Remote("com.intellij.openapi.editor.colors.EditorColorsScheme")
interface EditorColorsScheme {
  fun getEditorFontSize(): Int
}

@Remote("com.intellij.openapi.editor.SelectionModel")
interface SelectionModel {
  fun setSelection(startOffset: Int, endOffset: Int)
  fun getSelectedText(allCaret: Boolean = false): String?
  fun removeSelection()
}

@Remote("com.intellij.openapi.editor.markup.TextAttributes")
interface TextAttributes {
  fun getEffectType(): EffectType
  fun getEffectColor(): ColorRef?
  fun getForegroundColor(): ColorRef
}

@Remote("com.intellij.openapi.editor.markup.EffectType")
interface EffectType

fun Driver.openEditor(file: VirtualFile, project: Project? = null): Array<FileEditor> {
  return withContext(OnDispatcher.EDT) {
    service<FileEditorManager>(project ?: singleProject()).openFile(file, true, false)
  }
}

fun Driver.openFile(relativePath: String, project: Project = singleProject(), waitForCodeAnalysis: Boolean = true, isTextEditor: Boolean = true) {
  step("Open file $relativePath") {
    val openedFile = if (!isRemDevMode) {
      val fileToOpen = findFile(relativePath = relativePath, project = project)
      if (fileToOpen == null) {
        throw IllegalArgumentException("Fail to find file $relativePath")
      }
      openEditor(fileToOpen, project)
      fileToOpen
    }
    else {
      val service = service(GuestNavigationService::class, project)
      withContext(OnDispatcher.EDT) {
        service.navigateViaBackend(relativePath, 0)
        waitFor(message = "File is opened: $relativePath", timeout = 30.seconds,
                getter = {
                  if (isTextEditor) service<FileEditorManager>(project).getSelectedTextEditor()?.getVirtualFile()
                  else service<FileEditorManager>(project).getCurrentFile()
                },
                checker = { virtualFile ->
                  virtualFile != null &&
                  Path.of(virtualFile.getPath()).endsWith(Path.of(relativePath))
                })!!
      }
    }
    if (waitForCodeAnalysis) {
      waitForCodeAnalysis(project, openedFile)
    }
  }
}
