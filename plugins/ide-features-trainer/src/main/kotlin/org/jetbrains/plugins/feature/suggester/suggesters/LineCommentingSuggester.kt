package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.EditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.history.ChangesHistory
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import java.lang.ref.WeakReference
import kotlin.math.abs

class LineCommentingSuggester : FeatureSuggester {

    companion object {
        const val POPUP_MESSAGE = "Try the Comment Line feature to do it faster."
        const val SUGGESTING_ACTION_ID = "CommentByLineComment"
        const val DESCRIPTOR_ID = "codeassists.comment.line"
        const val NUMBER_OF_COMMENTS_TO_GET_SUGGESTION = 3
        const val MAX_TIME_MILLIS_INTERVAL_BETWEEN_COMMENTS = 5000
    }

    private data class DocumentLine(val startOffset: Int, val endOffset: Int, val text: String)
    private data class CommentData(val lineNumber: Int, val documentRef: WeakReference<Document>, val timeMillis: Long)

    private val commentsHistory = ChangesHistory<CommentData>(NUMBER_OF_COMMENTS_TO_GET_SUGGESTION)
    private var firstSlashAddedAction: EditorTextInsertedAction? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val curAction = actions.lastOrNull() ?: return NoSuggestion
        if (curAction is EditorTextInsertedAction) {
            if (isCommentSymbolAdded(curAction, '/')) {
                firstSlashAddedAction = curAction
            } else if (firstSlashAddedAction != null && isSecondSlashAdded(curAction, firstSlashAddedAction!!)
                || isCommentSymbolAdded(curAction, '#')
            ) {
                val document = curAction.documentRef.get() ?: return NoSuggestion
                val commentData = CommentData(
                    lineNumber = document.getLineNumber(curAction.offset),
                    documentRef = curAction.documentRef,
                    timeMillis = curAction.timeMillis
                )
                commentsHistory.add(commentData)
                firstSlashAddedAction = null

                if (commentsHistory.size == NUMBER_OF_COMMENTS_TO_GET_SUGGESTION
                    && commentsHistory.isLinesCommentedInARow()
                ) {
                    commentsHistory.clear()
                    return createSuggestion(
                        DESCRIPTOR_ID,
                        createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
                    )
                }
            }
        }

        return NoSuggestion
    }

    private fun isCommentSymbolAdded(action: EditorTextInsertedAction, symbol: Char): Boolean {
        with(action) {
            val psiFile = psiFileRef.get() ?: return false
            val document = documentRef.get() ?: return false
            if (text != symbol.toString()) return false
            val psiElement = psiFile.findElementAt(offset) ?: return false
            if (psiElement is PsiComment || psiElement.nextSibling is PsiComment) return false
            val line = document.getLineByOffset(offset)
            val lineBeforeSlash = line.text.substring(0, offset - line.startOffset)
            return lineBeforeSlash.isBlank() && line.text.trim() != symbol.toString()
        }
    }

    private fun isSecondSlashAdded(curAction: EditorTextInsertedAction, prevAction: EditorTextInsertedAction): Boolean {
        val curPsiFile = curAction.psiFileRef.get() ?: return false
        val curDocument = curAction.documentRef.get() ?: return false
        val prevPsiFile = prevAction.psiFileRef.get() ?: return false
        val prevDocument = curAction.documentRef.get() ?: return false
        if (curPsiFile !== prevPsiFile || curDocument !== prevDocument) return false
        return curAction.text == "/"
                && abs(curAction.offset - prevAction.offset) == 1
                && curDocument.getLineNumber(curAction.offset) == prevDocument.getLineNumber(prevAction.offset)
    }

    private fun ChangesHistory<CommentData>.isLinesCommentedInARow(): Boolean {
        val comments = asIterable()
        return !(comments.map(CommentData::lineNumber)
            .sorted()
            .zipWithNext { first, second -> second - first }
            .any { it != 1 }
                || comments.map { it.documentRef.get() }
            .zipWithNext { first, second -> first != null && first === second }
            .any { !it }
                || comments.map(CommentData::timeMillis)
            .zipWithNext { first, second -> second - first }
            .any { it > MAX_TIME_MILLIS_INTERVAL_BETWEEN_COMMENTS })
    }

    private fun Document.getLineByOffset(offset: Int): DocumentLine {
        val lineNumber = getLineNumber(offset)
        val startOffset = getLineStartOffset(lineNumber)
        val endOffset = getLineEndOffset(lineNumber)
        return DocumentLine(
            startOffset = startOffset,
            endOffset = endOffset,
            text = getText(TextRange(startOffset, endOffset))
        )
    }

    override fun getId(): String = "Commenting suggester"
}