package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorCopyPasteHelper
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

abstract class FeatureSuggesterTest : LightJavaCodeInsightFixtureTestCase() {

    protected abstract val testingCodeFileName: String

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

    fun subscribeToSuggestions(suggestionFound: (PopupSuggestion) -> Unit) {
        project.messageBus.connect()
            .subscribe(FeatureSuggestersManagerListener.TOPIC, object : FeatureSuggestersManagerListener {
                override fun featureFound(suggestion: PopupSuggestion) {
                    suggestionFound(suggestion)
                }
            })
    }

    fun assertSuggestedCorrectly(actionId: String, suggestionMessage: String) {
        TestCase.assertTrue(expectedSuggestion is PopupSuggestion)
        TestCase.assertEquals(
            FeatureSuggester.createMessageWithShortcut(actionId, suggestionMessage),
            (expectedSuggestion as PopupSuggestion).message
        )
    }

    fun moveCaretRelatively(columnShift: Int, lineShift: Int, withSelection: Boolean = false) {
        editor.caretModel.moveCaretRelatively(columnShift, lineShift, withSelection, false, true)
    }

    fun moveCaretToLogicalPosition(lineIndex: Int, columnIndex: Int) {
        editor.caretModel.moveToLogicalPosition(LogicalPosition(lineIndex, columnIndex))
    }

    fun moveCaretToOffset(offset: Int) {
        editor.caretModel.moveToOffset(offset)
    }

    fun selectBetweenLogicalPositions(
        lineStartIndex: Int, columnStartIndex: Int,
        lineEndIndex: Int, columnEndIndex: Int
    ) {
        moveCaretToLogicalPosition(lineStartIndex, columnStartIndex)
        moveCaretRelatively(columnEndIndex - columnStartIndex, lineEndIndex - lineStartIndex, true)
    }

    private fun performAction(
        actionId: String,
        perform: (AnAction, DataContext, AnActionEvent) -> Unit = { _, _, _ -> }
    ) {
        val action = ActionManager.getInstance().getAction(actionId)
        DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
            val event = AnActionEvent.createFromAnAction(action, null, "IDE Features Suggester", dataContext)
            val manager = ActionManagerEx.getInstanceEx()

            manager.fireBeforeActionPerformed(action, dataContext, event)
            perform(action, dataContext, event)
            ActionUtil.performActionDumbAware(action, event)
            manager.fireAfterActionPerformed(action, dataContext, event)
        }
    }

    fun copyCurrentSelection() {
        performAction("EditorCopy") { _, _, _ ->
            editor.selectionModel.copySelectionToClipboard()
        }
        editor.selectionModel.removeSelection()
    }

    fun cutCurrentSelection() {
        performAction("EditorCut")
        commitAllDocuments()
    }

    fun copyBetweenLogicalPositions(
        lineStartIndex: Int, columnStartIndex: Int,
        lineEndIndex: Int, columnEndIndex: Int
    ) {
        val oldOffset = editor.caretModel.offset
        selectBetweenLogicalPositions(lineStartIndex, columnStartIndex, lineEndIndex, columnEndIndex)
        copyCurrentSelection()
        editor.caretModel.moveToOffset(oldOffset)
    }

    fun cutBetweenLogicalPositions(
        lineStartIndex: Int, columnStartIndex: Int,
        lineEndIndex: Int, columnEndIndex: Int
    ) {
        val oldOffset = editor.caretModel.offset
        selectBetweenLogicalPositions(lineStartIndex, columnStartIndex, lineEndIndex, columnEndIndex)
        cutCurrentSelection()
        editor.caretModel.moveToOffset(oldOffset)
    }

    fun pasteFromClipboard() {
        ApplicationManager.getApplication().runWriteAction {
            CommandProcessor.getInstance().executeCommand(
                project,
                { EditorCopyPasteHelper.getInstance().pasteFromClipboard(editor) },
                "Paste",
                null
            )
        }
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
            findInFile(project, psiFile, findModel, fromOffset, isForward, isCaseSensitive)
        if (findResults.isNotEmpty()) {
            val firstOccurrence = findResults.first()
            moveCaretToOffset(firstOccurrence.endOffset - 1)
        }
        return findResults
    }

    fun findInFile(
        project: Project,
        psiFile: PsiFile,
        findModel: FindModel,
        fromOffset: Int = 0,
        isForward: Boolean = true,
        isCaseSensitive: Boolean = false
    ): List<TextRange> {
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

    fun commitAllDocuments() {
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

    fun deleteSymbolsAtCaret(symbolsNumber: Int) {
        (0 until symbolsNumber).forEach { _ -> deleteSymbolAtCaret() }
    }

    fun logicalPositionToOffset(lineIndex: Int, columnIndex: Int): Int {
        return editor.logicalPositionToOffset(LogicalPosition(lineIndex, columnIndex))
    }
}