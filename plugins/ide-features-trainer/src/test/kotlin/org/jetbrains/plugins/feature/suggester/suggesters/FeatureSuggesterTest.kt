package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.Assert
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.FeatureSuggestersManagerListener
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

abstract class FeatureSuggesterTest : LightJavaCodeInsightFixtureTestCase() {

    protected abstract val testingCodeFileName: String
    protected abstract val testingSuggesterId: String

    protected lateinit var expectedSuggestion: Suggestion

    override fun setUp() {
        super.setUp()
        myFixture.configureByFile(testingCodeFileName)
        expectedSuggestion = NoSuggestion
        subscribeToSuggestions { suggestion -> expectedSuggestion = suggestion }
    }

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    fun testInvokeLater(runnable: Runnable) {
        ApplicationManager.getApplication().invokeLater(runnable, project.disposed)
    }

    private fun subscribeToSuggestions(suggestionFound: (PopupSuggestion) -> Unit) {
        project.messageBus.connect()
            .subscribe(
                FeatureSuggestersManagerListener.TOPIC,
                object : FeatureSuggestersManagerListener {
                    override fun featureFound(suggestion: PopupSuggestion) {
                        suggestionFound(suggestion)
                    }
                }
            )
    }

    fun assertSuggestedCorrectly() {
        TestCase.assertTrue(expectedSuggestion is PopupSuggestion)
        TestCase.assertEquals(testingSuggesterId, (expectedSuggestion as PopupSuggestion).suggesterId)
    }

    fun moveCaretRelatively(columnShift: Int, lineShift: Int, withSelection: Boolean = false) {
        editor.caretModel.moveCaretRelatively(columnShift, lineShift, withSelection, false, true)
    }

    fun moveCaretToLogicalPosition(lineIndex: Int, columnIndex: Int) {
        editor.caretModel.moveToLogicalPosition(LogicalPosition(lineIndex, columnIndex))
    }

    private fun moveCaretToOffset(offset: Int) {
        editor.caretModel.moveToOffset(offset)
    }

    fun selectBetweenLogicalPositions(
        lineStartIndex: Int,
        columnStartIndex: Int,
        lineEndIndex: Int,
        columnEndIndex: Int
    ) {
        moveCaretToLogicalPosition(lineStartIndex, columnStartIndex)
        moveCaretRelatively(columnEndIndex - columnStartIndex, lineEndIndex - lineStartIndex, true)
    }

    fun copyCurrentSelection() {
        myFixture.performEditorAction("EditorCopy")
        editor.selectionModel.removeSelection()
    }

    private fun cutCurrentSelection() {
        myFixture.performEditorAction("EditorCut")
        commitAllDocuments()
    }

    fun copyBetweenLogicalPositions(
        lineStartIndex: Int,
        columnStartIndex: Int,
        lineEndIndex: Int,
        columnEndIndex: Int
    ) {
        doBetweenLogicalPositions(
            lineStartIndex,
            columnStartIndex,
            lineEndIndex,
            columnEndIndex,
            this::copyCurrentSelection
        )
    }

    fun cutBetweenLogicalPositions(
        lineStartIndex: Int,
        columnStartIndex: Int,
        lineEndIndex: Int,
        columnEndIndex: Int
    ) {
        doBetweenLogicalPositions(
            lineStartIndex,
            columnStartIndex,
            lineEndIndex,
            columnEndIndex,
            this::cutCurrentSelection
        )
    }

    fun deleteTextBetweenLogicalPositions(
        lineStartIndex: Int,
        columnStartIndex: Int,
        lineEndIndex: Int,
        columnEndIndex: Int
    ) {
        doBetweenLogicalPositions(
            lineStartIndex,
            columnStartIndex,
            lineEndIndex,
            columnEndIndex,
            this::deleteSymbolAtCaret
        )
    }

    private fun doBetweenLogicalPositions(
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

    fun pasteFromClipboard() {
        myFixture.performEditorAction("EditorPaste")
        commitAllDocuments()
    }

    fun performFindInFileAction(
        stringToFind: String,
        fromOffset: Int = 0,
        isForward: Boolean = true,
        isCaseSensitive: Boolean = false
    ): List<TextRange> {
        myFixture.performEditorAction("Find")
        val findModel = FindManager.getInstance(project).findInFileModel
        findModel.stringToFind = stringToFind
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return emptyList()
        val findResults: List<TextRange> =
            findInFile(psiFile, findModel, fromOffset, isForward, isCaseSensitive)
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

    fun focusEditor() {
        (editor as FocusListener).focusGained(FocusEvent(editor.component, 0))
    }

    fun type(text: String) {
        for (ch in text) {
            myFixture.type(ch)
            commitAllDocuments()
        }
    }

    private fun commitAllDocuments() {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    fun insertNewLineAt(lineIndex: Int, columnIndex: Int = 0) {
        moveCaretToLogicalPosition(lineIndex, columnIndex)
        type("\n")
        moveCaretRelatively(0, -1)
    }

    fun deleteSymbolAtCaret() {
        type("\b")
    }

    fun typeDelete() {
        ApplicationManager.getApplication().invokeAndWait {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
        }
    }

    fun deleteSymbolsAtCaret(symbolsNumber: Int) {
        (0 until symbolsNumber).forEach { _ -> deleteSymbolAtCaret() }
    }

    fun logicalPositionToOffset(lineIndex: Int, columnIndex: Int): Int {
        return editor.logicalPositionToOffset(LogicalPosition(lineIndex, columnIndex))
    }

    fun completeBasic(): Array<LookupElement>? {
        myFixture.performEditorAction("CodeCompletion")
        return myFixture.lookupElements
    }

    enum class CompletionFinishType(val value: Char) {
        NORMAL('\n'),
        REPLACE('\t'),
        AUTO_INSERT(Character.forDigit(0, 10)),
        COMPLETE_STATEMENT('\r')
    }

    fun chooseCompletionItem(item: LookupElement, finishType: CompletionFinishType = CompletionFinishType.NORMAL) {
        val lookup = getLookup() ?: return
        lookup.currentItem = item
        type(finishType.value.toString())
    }

    private fun getLookup(): LookupImpl? {
        return LookupManager.getInstance(project).activeLookup as LookupImpl?
    }

    fun getLookupElements(): Array<LookupElement>? {
        return myFixture.lookupElements
    }

    fun fail(): Nothing {
        Assert.fail()
        throw Exception()
    }
}
