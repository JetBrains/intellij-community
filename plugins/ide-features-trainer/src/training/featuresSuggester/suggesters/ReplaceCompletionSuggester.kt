package training.featuresSuggester.suggesters

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.refactoring.suggested.startOffset
import training.featuresSuggester.*
import training.featuresSuggester.actions.*

class ReplaceCompletionSuggester : AbstractFeatureSuggester() {
  override val id: String = "Completion with replace"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("replace.completion.name")

  override val message = FeatureSuggesterBundle.message("replace.completion.message")
  override val suggestingActionId = "EditorChooseLookupItemReplace"
  override val suggestingDocUrl = "https://www.jetbrains.com/help/idea/auto-completing-code.html#accept"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

  private data class EditedStatementData(val dotOffset: Int) {
    var isCompletionStarted: Boolean = false
    var textToDelete: String = ""
    var deletedText: String = ""
    var addedExprEndOffset: Int = -1

    val isCompletionFinished: Boolean
      get() = textToDelete != ""

    fun isAroundDot(offset: Int): Boolean {
      return offset in dotOffset..(dotOffset + 7)
    }

    fun isIdentifierNameDeleted(caretOffset: Int, newDeletedText: String): Boolean {
      return isCaretInRangeOfExprToDelete(caretOffset, newDeletedText) &&
             (deletedText.contains(textToDelete) || deletedText.contains(textToDelete.reversed()))
    }

    private fun isCaretInRangeOfExprToDelete(caretOffset: Int, newDeletedText: String): Boolean {
      return caretOffset in (addedExprEndOffset - 2)..(addedExprEndOffset + newDeletedText.length)
    }

    fun isDeletedTooMuch(): Boolean {
      return deletedText.length >= textToDelete.length * 2
    }
  }

  private var editedStatementData: EditedStatementData? = null

  override fun getSuggestion(action: Action): Suggestion {
    val language = action.language ?: return NoSuggestion
    val langSupport = SuggesterSupport.getForLanguage(language) ?: return NoSuggestion
    when (action) {
      is BeforeEditorTextRemovedAction -> {
        if (action.textFragment.text == ".") {
          editedStatementData = langSupport.createEditedStatementData(action, action.caretOffset)
        }
      }
      is BeforeEditorTextInsertedAction -> {
        if (editedStatementData != null &&
            action.text == "." &&
            action.caretOffset == editedStatementData!!.dotOffset
        ) {
          editedStatementData!!.isCompletionStarted = true
        }
      }
      is EditorCodeCompletionAction -> {
        val caretOffset = action.caretOffset
        if (caretOffset == 0) return NoSuggestion
        if (action.document.getText(TextRange(caretOffset - 1, caretOffset)) == ".") {
          editedStatementData = langSupport.createEditedStatementData(action, action.caretOffset)?.apply {
            isCompletionStarted = true
          }
        }
      }
      is BeforeCompletionChooseItemAction -> {
        if (editedStatementData == null || editedStatementData?.isAroundDot(action.caretOffset) != true) {
          return NoSuggestion
        }
        val curElement = action.psiFile?.findElementAt(action.caretOffset) ?: return NoSuggestion
        if (langSupport.isIdentifier(curElement)) {
          // remove characters that user typed before completion
          editedStatementData!!.textToDelete =
            curElement.text.substring(action.caretOffset - curElement.startOffset)
        }
      }
      is CompletionChooseItemAction -> {
        editedStatementData?.addedExprEndOffset = action.caretOffset
      }
      is EditorTextInsertedAction -> {
        if (editedStatementData?.isCompletionFinished != true) return NoSuggestion
        if (action.caretOffset < editedStatementData!!.addedExprEndOffset) {
          editedStatementData!!.addedExprEndOffset += action.text.length
        }
      }
      is EditorTextRemovedAction -> {
        if (editedStatementData?.isCompletionFinished != true) return NoSuggestion
        editedStatementData!!.deletedText += action.textFragment.text
        if (editedStatementData!!.isDeletedTooMuch()) {
          editedStatementData = null
        }
        else if (editedStatementData!!.isIdentifierNameDeleted(
            action.caretOffset,
            action.textFragment.text
          )
        ) {
          editedStatementData = null
          return createSuggestion()
        }
      }
      is EditorEscapeAction -> {
        editedStatementData = null
      }
    }

    return NoSuggestion
  }

  @Suppress("DuplicatedCode")
  private fun SuggesterSupport.createEditedStatementData(action: EditorAction, offset: Int): EditedStatementData? {
    val curElement = action.psiFile?.findElementAt(offset) ?: return null
    return if (curElement.getParentByPredicate(::isLiteralExpression) == null &&
               curElement.getParentOfType<PsiComment>() == null
    ) {
      EditedStatementData(offset)
    }
    else {
      null
    }
  }
}
