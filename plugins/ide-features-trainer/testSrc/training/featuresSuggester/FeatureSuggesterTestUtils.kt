// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

object FeatureSuggesterTestUtils {
  fun subscribeToSuggestions(project: Project, disposable: Disposable, suggestionFound: (PopupSuggestion) -> Unit) {
    project.messageBus.connect(disposable).subscribe(FeatureSuggestersManagerListener.TOPIC,
      object : FeatureSuggestersManagerListener {
        override fun featureFound(suggestion: PopupSuggestion) {
          suggestionFound(suggestion)
        }
      })
  }

  fun testInvokeLater(project: Project, runnable: Runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, project.disposed)
  }

  fun CodeInsightTestFixture.moveCaretRelatively(columnShift: Int, lineShift: Int, withSelection: Boolean = false) {
    editor.caretModel.moveCaretRelatively(columnShift, lineShift, withSelection, false, true)
  }

  fun CodeInsightTestFixture.moveCaretToLogicalPosition(lineIndex: Int, columnIndex: Int) {
    editor.caretModel.moveToLogicalPosition(LogicalPosition(lineIndex, columnIndex))
  }

  private fun CodeInsightTestFixture.moveCaretToOffset(offset: Int) {
    editor.caretModel.moveToOffset(offset)
  }

  fun CodeInsightTestFixture.selectBetweenLogicalPositions(
    lineStartIndex: Int,
    columnStartIndex: Int,
    lineEndIndex: Int,
    columnEndIndex: Int
  ) {
    moveCaretToLogicalPosition(lineStartIndex, columnStartIndex)
    moveCaretRelatively(columnEndIndex - columnStartIndex, lineEndIndex - lineStartIndex, true)
  }

  fun CodeInsightTestFixture.copyCurrentSelection() {
    performEditorAction("EditorCopy")
    editor.selectionModel.removeSelection()
  }

  private fun CodeInsightTestFixture.cutCurrentSelection() {
    performEditorAction("EditorCut")
    commitAllDocuments()
  }

  fun CodeInsightTestFixture.copyBetweenLogicalPositions(
    lineStartIndex: Int,
    columnStartIndex: Int,
    lineEndIndex: Int,
    columnEndIndex: Int
  ) {
    doBetweenLogicalPositions(
      lineStartIndex,
      columnStartIndex,
      lineEndIndex,
      columnEndIndex
    ) { copyCurrentSelection() }
  }

  fun CodeInsightTestFixture.cutBetweenLogicalPositions(
    lineStartIndex: Int,
    columnStartIndex: Int,
    lineEndIndex: Int,
    columnEndIndex: Int
  ) {
    doBetweenLogicalPositions(
      lineStartIndex,
      columnStartIndex,
      lineEndIndex,
      columnEndIndex
    ) { cutCurrentSelection() }
  }

  fun CodeInsightTestFixture.deleteTextBetweenLogicalPositions(
    lineStartIndex: Int,
    columnStartIndex: Int,
    lineEndIndex: Int,
    columnEndIndex: Int
  ) {
    doBetweenLogicalPositions(
      lineStartIndex,
      columnStartIndex,
      lineEndIndex,
      columnEndIndex
    ) { deleteSymbolAtCaret() }
  }

  private fun CodeInsightTestFixture.doBetweenLogicalPositions(
    lineStartIndex: Int,
    columnStartIndex: Int,
    lineEndIndex: Int,
    columnEndIndex: Int,
    action: () -> Unit
  ) {
    val oldOffset = editor.caretModel.offset
    selectBetweenLogicalPositions(lineStartIndex, columnStartIndex, lineEndIndex, columnEndIndex)
    action()
    editor.caretModel.moveToOffset(oldOffset)
  }

  fun CodeInsightTestFixture.pasteFromClipboard() {
    performEditorAction("EditorPaste")
    commitAllDocuments()
  }

  fun CodeInsightTestFixture.performFindInFileAction(
    stringToFind: String,
    fromOffset: Int = 0,
    isForward: Boolean = true,
    isCaseSensitive: Boolean = false
  ): List<TextRange> {
    performEditorAction("Find")
    val findModel = FindManager.getInstance(project).findInFileModel
    findModel.stringToFind = stringToFind
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return emptyList()
    val findResults: List<TextRange> = findInFile(psiFile, findModel, fromOffset, isForward, isCaseSensitive)
    if (findResults.isNotEmpty()) {
      val firstOccurrence = findResults.first()
      moveCaretToOffset(firstOccurrence.endOffset - 1)
    }
    return findResults
  }

  private fun findInFile(
    psiFile: PsiFile,
    findModel: FindModel,
    fromOffset: Int = 0,
    isForward: Boolean = true,
    isCaseSensitive: Boolean = false
  ): List<TextRange> {
    val project = psiFile.project
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return emptyList()
    val text = document.charsSequence
    val textLength = document.textLength
    val findManager = FindManager.getInstance(project)
    findModel.apply {
      this.isForward = isForward
      this.isCaseSensitive = isCaseSensitive
    }
    var offset = fromOffset
    val virtualFile = psiFile.virtualFile
    val findResults: MutableList<TextRange> = arrayListOf()
    while (offset < textLength) {
      val result = findManager.findString(text, offset, findModel, virtualFile)
      if (!result.isStringFound) break
      findResults.add(TextRange(result.startOffset, result.endOffset))
      val prevOffset = offset
      offset = result.endOffset
      if (prevOffset == offset) {
        // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
        ++offset
      }
    }
    return findResults
  }

  fun CodeInsightTestFixture.focusEditor() {
    (editor as FocusListener).focusGained(FocusEvent(editor.component, 0))
  }

  fun CodeInsightTestFixture.typeAndCommit(text: String) {
    for (ch in text) {
      type(ch)
      commitAllDocuments()
    }
  }

  private fun CodeInsightTestFixture.commitAllDocuments() {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }

  fun CodeInsightTestFixture.insertNewLineAt(lineIndex: Int, columnIndex: Int = 0) {
    moveCaretToLogicalPosition(lineIndex, columnIndex)
    typeAndCommit("\n")
    moveCaretRelatively(0, -1)
  }

  fun CodeInsightTestFixture.deleteSymbolAtCaret() {
    typeAndCommit("\b")
  }

  fun CodeInsightTestFixture.typeDelete() {
    ApplicationManager.getApplication().invokeAndWait {
      performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
    }
  }

  fun CodeInsightTestFixture.deleteSymbolsAtCaret(symbolsNumber: Int) {
    (0 until symbolsNumber).forEach { _ -> deleteSymbolAtCaret() }
  }

  fun CodeInsightTestFixture.logicalPositionToOffset(lineIndex: Int, columnIndex: Int): Int {
    return editor.logicalPositionToOffset(LogicalPosition(lineIndex, columnIndex))
  }

  fun CodeInsightTestFixture.invokeCodeCompletion(): Array<LookupElement>? {
    performEditorAction("CodeCompletion")
    return lookupElements
  }

  enum class CompletionFinishType(val value: Char) {
    NORMAL('\n'),
    REPLACE('\t'),
    AUTO_INSERT(Character.forDigit(0, 10)),
    COMPLETE_STATEMENT('\r')
  }

  fun CodeInsightTestFixture.chooseCompletionItem(item: LookupElement, finishType: CompletionFinishType = CompletionFinishType.NORMAL) {
    val lookup = lookup ?: return
    lookup.currentItem = item
    typeAndCommit(finishType.value.toString())
  }
}