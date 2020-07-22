package org.jetbrains.plugins.feature.suggester.suggesters

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
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.*

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

}