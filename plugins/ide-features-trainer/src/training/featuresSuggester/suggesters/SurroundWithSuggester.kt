package training.featuresSuggester.suggesters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.SuggesterSupport
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.*
import training.util.WeakReferenceDelegator

class SurroundWithSuggester : AbstractFeatureSuggester() {
  override val id: String = "Surround with"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("surround.with.name")

  override val message = FeatureSuggesterBundle.message("surround.with.message")
  override val suggestingActionId = "SurroundWith"
  override val suggestingTipFileName = "SurroundWith.html"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf("JAVA", "kotlin")

  private object State {
    var surroundingStatement: PsiElement? by WeakReferenceDelegator(null)
    var surroundingStatementStartOffset: Int = -1
    var firstStatementInBlockText: String = ""
    var isLeftBraceAdded: Boolean = false
    var isRightBraceAdded: Boolean = false
    var lastChangeTimeMillis: Long = 0L
    var lastTextInsertedAction: EditorTextInsertedAction? by WeakReferenceDelegator(null)

    fun applySurroundingStatementAddition(statement: PsiElement, timeMillis: Long) {
      reset()
      surroundingStatement = statement
      surroundingStatementStartOffset = statement.startOffset
      lastChangeTimeMillis = timeMillis
    }

    fun applyBraceAddition(timeMillis: Long, braceType: String) {
      lastChangeTimeMillis = timeMillis
      if (braceType == "{") {
        isLeftBraceAdded = true
      }
      else {
        isRightBraceAdded = true
      }
    }

    fun isOutOfDate(newChangeTimeMillis: Long): Boolean {
      return lastChangeTimeMillis != 0L &&
             newChangeTimeMillis - lastChangeTimeMillis > MAX_TIME_MILLIS_BETWEEN_ACTIONS
    }

    fun reset() {
      surroundingStatement = null
      surroundingStatementStartOffset = -1
      firstStatementInBlockText = ""
      isLeftBraceAdded = false
      isRightBraceAdded = false
      lastChangeTimeMillis = 0L
      lastTextInsertedAction = null
    }
  }

  @Suppress("NestedBlockDepth")
  override fun getSuggestion(action: Action): Suggestion {
    val language = action.language ?: return NoSuggestion
    val langSupport = SuggesterSupport.getForLanguage(language) ?: return NoSuggestion
    if (action is PsiAction && State.run { surroundingStatementStartOffset != -1 && surroundingStatement?.isValid == false }) {
      State.tryToUpdateSurroundingStatement(langSupport, action)
    }
    when (action) {
      is ChildReplacedAction -> {
        @Suppress("ComplexCondition")
        if (langSupport.isIfStatement(action.newChild) && action.oldChild.text == "i" ||
            langSupport.isForStatement(action.newChild) && action.oldChild.text == "fo" ||
            langSupport.isWhileStatement(action.newChild) && action.oldChild.text == "whil"
        ) {
          State.applySurroundingStatementAddition(action.newChild, action.timeMillis)
        }
      }
      is ChildAddedAction -> {
        if (langSupport.isSurroundingStatement(action.newChild)) {
          State.applySurroundingStatementAddition(action.newChild, action.timeMillis)
        }
      }
      is ChildrenChangedAction -> {
        if (State.surroundingStatement == null) return NoSuggestion
        val textInsertedAction = State.lastTextInsertedAction ?: return NoSuggestion
        val text = textInsertedAction.text
        if (text.contains("{") || text.contains("}")) {
          val psiFile = action.parent as? PsiFile ?: return NoSuggestion
          if (State.isOutOfDate(action.timeMillis) || text != "{" && text != "}") {
            State.reset()
          }
          else if (text == "{") {
            if (State.isLeftBraceAdded) {
              State.reset()
            }
            else if (State.isBraceAddedToStatement(
                langSupport,
                psiFile,
                textInsertedAction.caretOffset
              )
            ) {
              State.applyBraceAddition(action.timeMillis, "{")
              State.saveFirstStatementInBlock(langSupport)
            }
          }
          else {
            if (State.isLeftBraceAdded &&
                State.isBraceAddedToStatement(langSupport, psiFile, textInsertedAction.caretOffset)
            ) {
              State.applyBraceAddition(action.timeMillis, "}")
              if (State.isStatementsSurrounded(langSupport)) {
                State.reset()
                return createSuggestion()
              }
            }
            State.reset()
          }
        }
      }
      is EditorTextInsertedAction -> {
        State.lastTextInsertedAction = action
      }
      else -> NoSuggestion
    }
    return NoSuggestion
  }

  private fun State.tryToUpdateSurroundingStatement(langSupport: SuggesterSupport, action: PsiAction) {
    val element = action.psiFile.findElementAt(surroundingStatementStartOffset) ?: return
    val parent = element.parent ?: return
    if (langSupport.isSurroundingStatement(parent)) {
      surroundingStatement = parent
    }
  }

  private fun State.isBraceAddedToStatement(langSupport: SuggesterSupport, psiFile: PsiFile, offset: Int): Boolean {
    val curElement = psiFile.findElementAt(offset) ?: return false
    return curElement.parent === langSupport.getCodeBlock(surroundingStatement!!)
  }

  private fun State.saveFirstStatementInBlock(langSupport: SuggesterSupport) {
    val statements = langSupport.getStatementsOfBlock(surroundingStatement!!)
    if (statements.isNotEmpty()) {
      firstStatementInBlockText = statements.first().text
    }
  }

  private fun State.isStatementsSurrounded(langSupport: SuggesterSupport): Boolean {
    if (surroundingStatement?.isValid == false ||
        !isLeftBraceAdded ||
        !isRightBraceAdded
    ) {
      return false
    }
    val statements = langSupport.getStatementsOfBlock(surroundingStatement!!)
    return statements.isNotEmpty() &&
           statements.first().text == firstStatementInBlockText
  }

  private fun SuggesterSupport.getStatementsOfBlock(psiElement: PsiElement): List<PsiElement> {
    val codeBlock = getCodeBlock(psiElement)
    return if (codeBlock != null) {
      getStatements(codeBlock)
    }
    else {
      emptyList()
    }
  }

  private fun SuggesterSupport.isSurroundingStatement(psiElement: PsiElement): Boolean {
    return isIfStatement(psiElement) || isForStatement(psiElement) || isWhileStatement(psiElement)
  }

  companion object {
    const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 8000L
  }
}
