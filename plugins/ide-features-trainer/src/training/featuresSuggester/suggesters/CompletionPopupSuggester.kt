package training.featuresSuggester.suggesters

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import training.featuresSuggester.*
import training.featuresSuggester.actions.*

class CompletionPopupSuggester : AbstractFeatureSuggester() {
  override val id = "Completion"
  override val suggestingActionDisplayName = FeatureSuggesterBundle.message("completion.popup.name")

  override val message = FeatureSuggesterBundle.message("completion.popup.message")
  override val suggestingActionId = "CodeCompletion"
  override val suggestingTipFileName = "CodeCompletion.html"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

  private object State {
    var dotOffset: Int = -1
      private set
    var isCompletionStarted: Boolean = false
      private set
    var lastChangeTimeMillis: Long = 0L
      private set

    val isDotRemoved: Boolean
      get() = dotOffset != -1

    fun applyDotRemoving(offset: Int, timeMillis: Long) {
      dotOffset = offset
      lastChangeTimeMillis = timeMillis
    }

    fun applyCompletionStarting(timeMillis: Long) {
      isCompletionStarted = true
      lastChangeTimeMillis = timeMillis
    }

    fun isAroundDot(offset: Int): Boolean {
      return offset in dotOffset..(dotOffset + 7)
    }

    fun isOutOfDate(newChangeTimeMillis: Long): Boolean {
      return newChangeTimeMillis - lastChangeTimeMillis > MAX_TIME_MILLIS_BETWEEN_ACTIONS
    }

    fun reset() {
      dotOffset = -1
      isCompletionStarted = false
      lastChangeTimeMillis = 0L
    }
  }

  override fun getSuggestion(action: Action): Suggestion {
    val language = action.language ?: return NoSuggestion
    val langSupport = SuggesterSupport.getForLanguage(language) ?: return NoSuggestion
    when (action) {
      is BeforeEditorTextRemovedAction -> {
        if (action.textFragment.text == ".") {
          State.reset()
          val psiFile = action.psiFile ?: return NoSuggestion
          if (!langSupport.isInsideCommentOrLiteral(psiFile, action.caretOffset)) {
            State.applyDotRemoving(action.caretOffset, action.timeMillis)
          }
        }
      }
      is BeforeEditorTextInsertedAction -> {
        if (action.text == ".") {
          if (State.isDotRemoved &&
              action.caretOffset == State.dotOffset &&
              !State.isOutOfDate(action.timeMillis)
          ) {
            State.applyCompletionStarting(action.timeMillis)
          }
          else {
            State.reset()
          }
        }
      }
      is BeforeCompletionChooseItemAction -> {
        try {
          if (State.isCompletionStarted &&
              State.isAroundDot(action.caretOffset) &&
              !State.isOutOfDate(action.timeMillis)
          ) {
            return createSuggestion()
          }
        }
        finally {
          State.reset()
        }
      }
      is EditorEscapeAction -> {
        State.reset()
      }
    }

    return NoSuggestion
  }

  private fun SuggesterSupport.isInsideCommentOrLiteral(psiFile: PsiFile, offset: Int): Boolean {
    val curElement = psiFile.findElementAt(offset) ?: return false
    return curElement.getParentByPredicate(::isLiteralExpression) != null ||
           curElement.getParentOfType<PsiComment>() != null
  }

  companion object {
    const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 5000L
  }
}
