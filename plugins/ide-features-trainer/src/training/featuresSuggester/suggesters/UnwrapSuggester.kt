package training.featuresSuggester.suggesters

import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import training.featuresSuggester.*
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.BeforeEditorTextRemovedAction

class UnwrapSuggester : AbstractFeatureSuggester() {
  override val id: String = "Unwrap"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("unwrap.name")

  override val message = FeatureSuggesterBundle.message("unwrap.message")
  override val suggestingActionId = "Unwrap"
  override val suggestingDocUrl =
    "https://www.jetbrains.com/help/idea/working-with-source-code.html#unwrap_remove_statement"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf("JAVA", "kotlin", "ECMAScript 6")

  private object State {
    var surroundingStatementStartOffset: Int = -1
    var closeBraceOffset: Int = -1
    var lastChangeTimeMillis: Long = 0L

    val isInitial: Boolean
      get() = surroundingStatementStartOffset == -1 && closeBraceOffset == -1

    fun isOutOfDate(newChangeTimeMillis: Long): Boolean {
      return newChangeTimeMillis - lastChangeTimeMillis > MAX_TIME_MILLIS_BETWEEN_ACTIONS
    }

    fun reset() {
      surroundingStatementStartOffset = -1
      closeBraceOffset = -1
      lastChangeTimeMillis = 0L
    }
  }

  private val surroundingStatementStartRegex = Regex("""[ \n]*(if|for|while)[ \n]*\(.*\)[ \n]*\{[ \n]*""")

  override fun getSuggestion(action: Action): Suggestion {
    val language = action.language ?: return NoSuggestion
    val langSupport = SuggesterSupport.getForLanguage(language) ?: return NoSuggestion
    if (action is BeforeEditorTextRemovedAction) {
      val text = action.textFragment.text
      when {
        text == "}" -> return langSupport.handleCloseBraceDeleted(action)
        text.matches(surroundingStatementStartRegex) -> {
          return langSupport.handleStatementStartDeleted(action)
        }
        else -> State.reset()
      }
    }
    return NoSuggestion
  }

  private fun SuggesterSupport.handleCloseBraceDeleted(action: BeforeEditorTextRemovedAction): Suggestion {
    when {
      State.isInitial -> handleCloseBraceDeletedFirst(action)
      State.closeBraceOffset != -1 -> {
        try {
          if (State.checkCloseBraceDeleted(action)) return createSuggestion()
        }
        finally {
          State.reset()
        }
      }
      else -> State.reset()
    }
    return NoSuggestion
  }

  private fun SuggesterSupport.handleStatementStartDeleted(action: BeforeEditorTextRemovedAction): Suggestion {
    when {
      State.isInitial -> handleStatementStartDeletedFirst(action)
      State.surroundingStatementStartOffset != -1 -> {
        try {
          if (State.checkStatementStartDeleted(action)) return createSuggestion()
        }
        finally {
          State.reset()
        }
      }
      else -> State.reset()
    }
    return NoSuggestion
  }

  private fun SuggesterSupport.handleCloseBraceDeletedFirst(action: BeforeEditorTextRemovedAction) {
    val curElement = action.psiFile?.findElementAt(action.caretOffset) ?: return
    val codeBlock = curElement.parent
    if (!isCodeBlock(codeBlock)) return
    val statements = getStatements(codeBlock)
    if (statements.isNotEmpty()) {
      val statement = getParentStatementOfBlock(codeBlock) ?: return
      if (isSurroundingStatement(statement)) {
        State.surroundingStatementStartOffset = statement.startOffset
        State.lastChangeTimeMillis = action.timeMillis
      }
    }
  }

  private fun State.checkStatementStartDeleted(action: BeforeEditorTextRemovedAction): Boolean {
    return !isOutOfDate(action.timeMillis) &&
           surroundingStatementStartOffset == action.textFragment.contentStartOffset
  }

  private fun SuggesterSupport.handleStatementStartDeletedFirst(action: BeforeEditorTextRemovedAction) {
    val textFragment = action.textFragment
    val curElement = action.psiFile?.findElementAt(textFragment.contentStartOffset) ?: return
    val curStatement = curElement.parent
    if (isSurroundingStatement(curStatement)) {
      val codeBlock = getCodeBlock(curStatement) ?: return
      val statements = getStatements(codeBlock)
      if (statements.isNotEmpty()) {
        State.closeBraceOffset = curStatement.endOffset - textFragment.text.length - 1
        State.lastChangeTimeMillis = action.timeMillis
      }
    }
  }

  private fun State.checkCloseBraceDeleted(action: BeforeEditorTextRemovedAction): Boolean {
    return !isOutOfDate(action.timeMillis) && action.caretOffset == closeBraceOffset
  }

  private val TextFragment.contentStartOffset: Int
    get() {
      val countOfStartDelimiters = text.indexOfFirst { it != ' ' && it != '\n' }
      return startOffset + countOfStartDelimiters
    }

  private fun SuggesterSupport.isSurroundingStatement(psiElement: PsiElement): Boolean {
    return isIfStatement(psiElement) || isForStatement(psiElement) || isWhileStatement(psiElement)
  }

  companion object {
    const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 7000L
  }
}
