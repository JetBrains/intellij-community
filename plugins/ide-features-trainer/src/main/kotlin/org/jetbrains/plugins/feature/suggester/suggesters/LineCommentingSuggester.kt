package org.jetbrains.plugins.feature.suggester.suggesters

import com.google.common.collect.EvictingQueue
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import org.jetbrains.plugins.feature.suggester.FeatureSuggesterBundle
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.EditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.util.WeakReferenceDelegator
import java.lang.ref.WeakReference
import java.util.Queue
import kotlin.math.abs

class LineCommentingSuggester : AbstractFeatureSuggester() {
    override val id: String = "Comment with line comment"
    override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("line.commenting.name")

    override val message = FeatureSuggesterBundle.message("line.commenting.message")
    override val suggestingActionId = "CommentByLineComment"
    override val suggestingTipFileName = "CommentCode.html"

    override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

    private data class DocumentLine(val startOffset: Int, val endOffset: Int, val text: String)
    private data class CommentData(val lineNumber: Int, val documentRef: WeakReference<Document>, val timeMillis: Long)

    override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

    private val maxTimeMillisBetweenComments = 5000L
    private val numberOfCommentsToGetSuggestion = 3
    @Suppress("UnstableApiUsage")
    private val commentsHistory: Queue<CommentData> = EvictingQueue.create(NUMBER_OF_COMMENTS_TO_GET_SUGGESTION)
    private var firstSlashAddedAction: EditorTextInsertedAction? by WeakReferenceDelegator(null)

    override fun getSuggestion(action: Action): Suggestion {
        if (action is EditorTextInsertedAction) {
            if (isCommentSymbolAdded(action, '/')) {
                firstSlashAddedAction = action
            } else if (firstSlashAddedAction != null && isSecondSlashAdded(action, firstSlashAddedAction!!) ||
                isCommentSymbolAdded(action, '#')
            ) {
                val document = action.document ?: return NoSuggestion
                val commentData = CommentData(
                    lineNumber = document.getLineNumber(action.caretOffset),
                    documentRef = WeakReference(document),
                    timeMillis = action.timeMillis
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
        if (curAction.project != prevAction.project) return false
        val curPsiFile = curAction.psiFile ?: return false
        val curDocument = curAction.document ?: return false
        val prevPsiFile = prevAction.psiFile ?: return false
        val prevDocument = curAction.document ?: return false
        if (curPsiFile !== prevPsiFile || curDocument !== prevDocument) return false
        return curAction.text == "/" &&
            abs(curAction.caretOffset - prevAction.caretOffset) == 1 &&
            curDocument.getLineNumber(curAction.caretOffset) == prevDocument.getLineNumber(prevAction.caretOffset)
    }

    private fun Queue<CommentData>.isLinesCommentedInARow(): Boolean {
        return !(
            map(CommentData::lineNumber)
                .sorted()
                .zipWithNext { first, second -> second - first }
                .any { it != 1 } ||
                map { it.documentRef.get() }
                    .zipWithNext { first, second -> first != null && first === second }
                    .any { !it } ||
                map(CommentData::timeMillis)
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
