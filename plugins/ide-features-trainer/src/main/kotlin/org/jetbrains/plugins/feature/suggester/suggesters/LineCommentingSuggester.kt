package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.EditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.history.ChangesHistory
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import java.lang.ref.WeakReference
import kotlin.math.abs

class LineCommentingSuggester : AbstractFeatureSuggester() {
    override val id: String = "Comment with line comment"
    override val suggestingActionDisplayName: String = "Comment with line comment"

    override val message = "Try the Comment Line feature to do it faster."
    override val suggestingActionId = "CommentByLineComment"
    override val suggestingTipFileName = "CommentCode.html"

    override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

    private data class DocumentLine(val startOffset: Int, val endOffset: Int, val text: String)
    private data class CommentData(val lineNumber: Int, val documentRef: WeakReference<Document>, val timeMillis: Long)

    override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

    private val maxTimeMillisBetweenComments = 5000L
    private val numberOfCommentsToGetSuggestion = 3
    private val commentsHistory = ChangesHistory<CommentData>(NUMBER_OF_COMMENTS_TO_GET_SUGGESTION)
    private var firstSlashAddedAction: EditorTextInsertedAction? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val curAction = actions.lastOrNull() ?: return NoSuggestion
        if (curAction is EditorTextInsertedAction) {
            if (isCommentSymbolAdded(curAction, '/')) {
                firstSlashAddedAction = curAction
            } else if (firstSlashAddedAction != null && isSecondSlashAdded(curAction, firstSlashAddedAction!!) ||
                isCommentSymbolAdded(curAction, '#')
            ) {
                val document = curAction.document ?: return NoSuggestion
                val commentData = CommentData(
                    lineNumber = document.getLineNumber(curAction.caretOffset),
                    documentRef = WeakReference(document),
                    timeMillis = curAction.timeMillis
                )
                commentsHistory.add(commentData)
                firstSlashAddedAction = null

                if (commentsHistory.size == numberOfCommentsToGetSuggestion &&
                    commentsHistory.isLinesCommentedInARow()
                ) {
                    commentsHistory.clear()
                    return createSuggestion()
                }
            }
        }
        return NoSuggestion
    }

    private fun isCommentSymbolAdded(action: EditorTextInsertedAction, symbol: Char): Boolean {
        with(action) {
            val psiFile = this.psiFile ?: return false
            val document = this.document ?: return false
            if (text != symbol.toString()) return false
            val psiElement = psiFile.findElementAt(caretOffset) ?: return false
            if (psiElement is PsiComment || psiElement.nextSibling is PsiComment) return false
            val line = document.getLineByOffset(caretOffset)
            val lineBeforeSlash = line.text.substring(0, caretOffset - line.startOffset)
            return lineBeforeSlash.isBlank() && line.text.trim() != symbol.toString()
        }
    }

    private fun isSecondSlashAdded(curAction: EditorTextInsertedAction, prevAction: EditorTextInsertedAction): Boolean {
        val curPsiFile = curAction.psiFile ?: return false
        val curDocument = curAction.document ?: return false
        val prevPsiFile = prevAction.psiFile ?: return false
        val prevDocument = curAction.document ?: return false
        if (curPsiFile !== prevPsiFile || curDocument !== prevDocument) return false
        return curAction.text == "/" &&
            abs(curAction.caretOffset - prevAction.caretOffset) == 1 &&
            curDocument.getLineNumber(curAction.caretOffset) == prevDocument.getLineNumber(prevAction.caretOffset)
    }

    private fun ChangesHistory<CommentData>.isLinesCommentedInARow(): Boolean {
        val comments = asIterable()
        return !(
                comments.map(CommentData::lineNumber)
                    .sorted()
                    .zipWithNext { first, second -> second - first }
                    .any { it != 1 } ||
                        comments.map { it.documentRef.get() }
                            .zipWithNext { first, second -> first != null && first === second }
                            .any { !it } ||
                        comments.map(CommentData::timeMillis)
                            .zipWithNext { first, second -> second - first }
                            .any { it > maxTimeMillisBetweenComments }
                )
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
}
