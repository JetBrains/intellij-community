package training.featuresSuggester.suggesters

import com.google.common.collect.EvictingQueue
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.EditorTextInsertedAction
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.abs

class LineCommentingSuggester : AbstractFeatureSuggester() {
  override val id: String = "Comment with line comment"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("line.commenting.name")

  override val message = FeatureSuggesterBundle.message("line.commenting.message")
  override val suggestingActionId = "CommentByLineComment"
  override val suggestingTipFileName = "CommentCode.html"
  override val minSuggestingIntervalDays = 14

  private data class DocumentLine(val startOffset: Int, val endOffset: Int, val text: String)
  private data class CommentData(val lineNumber: Int, val documentRef: WeakReference<Document>, val timeMillis: Long)
  private data class CommentSymbolPlace(val offset: Int, val filePath: String)

  override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

  private val maxTimeMillisBetweenComments = 5000L
  private val numberOfCommentsToGetSuggestion = 3

  @Suppress("UnstableApiUsage")
  private val commentsHistory: Queue<CommentData> = EvictingQueue.create(numberOfCommentsToGetSuggestion)
  private var prevCommentSymbolPlace: CommentSymbolPlace? = null

  override fun getSuggestion(action: Action): Suggestion {
    if (action is EditorTextInsertedAction) {
      if (isCommentSymbolAdded(action, '/')) {
        val fileName = action.psiFile?.virtualFile?.path ?: return NoSuggestion
        prevCommentSymbolPlace = CommentSymbolPlace(action.caretOffset, fileName)
      }
      else if (prevCommentSymbolPlace?.let { isSecondSlashAdded(action, it) } == true || isCommentSymbolAdded(action, '#')) {
        val document = action.document
        val commentData = CommentData(
          lineNumber = document.getLineNumber(action.caretOffset),
          documentRef = WeakReference(document),
          timeMillis = action.timeMillis
        )
        commentsHistory.add(commentData)
        prevCommentSymbolPlace = null

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
      if (text != symbol.toString()) return false
      val psiElement = psiFile.findElementAt(caretOffset) ?: return false
      if (psiElement is PsiComment || psiElement.nextSibling is PsiComment) return false
      val line = document.getLineByOffset(caretOffset)
      val lineBeforeSlash = line.text.substring(0, caretOffset - line.startOffset)
      return lineBeforeSlash.isBlank() && line.text.trim() != symbol.toString()
    }
  }

  private fun isSecondSlashAdded(curAction: EditorTextInsertedAction, prevSymbol: CommentSymbolPlace): Boolean {
    return curAction.text == "/"
           && abs(curAction.caretOffset - prevSymbol.offset) == 1
           && curAction.psiFile?.virtualFile?.path == prevSymbol.filePath
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
