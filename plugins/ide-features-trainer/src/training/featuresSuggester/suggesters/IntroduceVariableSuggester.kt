package training.featuresSuggester.suggesters

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import training.featuresSuggester.*
import training.featuresSuggester.actions.*
import training.util.WeakReferenceDelegator
import java.awt.datatransfer.DataFlavor

class IntroduceVariableSuggester : AbstractFeatureSuggester() {
  override val id: String = "Introduce variable"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("introduce.variable.name")

  override val message = FeatureSuggesterBundle.message("introduce.variable.message")
  override val suggestingActionId = "IntroduceVariable"
  override val suggestingTipFileName = "IntroduceVariable.html"
  override val minSuggestingIntervalDays = 14

  override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

  private class ExtractedExpressionData(var exprText: String, changedStatement: PsiElement) {
    var changedStatement: PsiElement? by WeakReferenceDelegator(changedStatement)
    val changedStatementText: String
    var declaration: PsiElement? by WeakReferenceDelegator(null)
    var variableEditingFinished: Boolean = false

    init {
      val text = changedStatement.text
      changedStatementText = text.replaceFirst(exprText, "").trim()
      exprText = exprText.trim()
    }

    fun getDeclarationText(): String? {
      return declaration?.let {
        if (it.isValid) it.text else null
      }
    }
  }

  private var extractedExprData: ExtractedExpressionData? = null

  override fun getSuggestion(action: Action): Suggestion {
    val language = action.language ?: return NoSuggestion
    val langSupport = SuggesterSupport.getForLanguage(language) ?: return NoSuggestion
    when (action) {
      is BeforeEditorTextRemovedAction -> {
        with(action) {
          val deletedText = getCopiedContent(textFragment.text) ?: return NoSuggestion
          val psiFile = this.psiFile ?: return NoSuggestion
          val contentOffset = caretOffset + textFragment.text.indexOfFirst { it != ' ' && it != '\n' }
          val curElement = psiFile.findElementAt(contentOffset) ?: return NoSuggestion
          if (langSupport.isPartOfExpression(curElement)) {
            val changedStatement =
              langSupport.getTopmostStatementWithText(curElement, deletedText) ?: return NoSuggestion
            extractedExprData = ExtractedExpressionData(textFragment.text, changedStatement)
          }
        }
      }
      is ChildReplacedAction -> {
        if (extractedExprData == null) return NoSuggestion
        with(action) {
          when {
            langSupport.isVariableDeclarationAdded(this) -> {
              extractedExprData!!.declaration = newChild
            }
            newChild.text.trim() == extractedExprData!!.changedStatementText -> {
              extractedExprData!!.changedStatement = newChild
            }
            langSupport.isVariableInserted(this) -> {
              extractedExprData = null
              return createSuggestion()
            }
          }
        }
      }
      is ChildAddedAction -> {
        if (extractedExprData == null) return NoSuggestion
        with(action) {
          if (langSupport.isVariableDeclarationAdded(this)) {
            extractedExprData!!.declaration = newChild
          }
          else if (newChild.text.trim() == extractedExprData!!.changedStatementText) {
            extractedExprData!!.changedStatement = newChild
          }
          else if (!extractedExprData!!.variableEditingFinished && isVariableEditingFinished()) {
            extractedExprData!!.variableEditingFinished = true
          }
        }
      }
      is ChildrenChangedAction -> {
        if (extractedExprData == null) return NoSuggestion
        if (action.parent === extractedExprData!!.declaration &&
            !extractedExprData!!.variableEditingFinished &&
            isVariableEditingFinished()
        ) {
          extractedExprData!!.variableEditingFinished = true
        }
      }
      else -> NoSuggestion
    }
    return NoSuggestion
  }

  private fun getCopiedContent(text: String): String? {
    if (text.isBlank()) return null
    val content = text.trim()
    val copyPasteManager = CopyPasteManager.getInstance()
    return if (copyPasteManager.areDataFlavorsAvailable(DataFlavor.stringFlavor) &&
               content == copyPasteManager.contents?.asString()?.trim()
    ) {
      content
    }
    else {
      null
    }
  }

  private fun SuggesterSupport.isVariableDeclarationAdded(action: ChildReplacedAction): Boolean {
    return isExpressionStatement(action.oldChild) && isVariableDeclaration(action.newChild)
  }

  private fun SuggesterSupport.isVariableDeclarationAdded(action: ChildAddedAction): Boolean {
    return isCodeBlock(action.parent) && isVariableDeclaration(action.newChild)
  }

  private fun isVariableEditingFinished(): Boolean {
    if (extractedExprData == null) return false
    with(extractedExprData!!) {
      val declarationText = getDeclarationText() ?: return false
      return declarationText.trim().endsWith(exprText)
    }
  }

  private fun SuggesterSupport.isVariableInserted(action: ChildReplacedAction): Boolean {
    if (extractedExprData == null) return false
    with(extractedExprData!!) {
      return variableEditingFinished
             && declaration.let { it != null && it.isValid && action.newChild.text == getVariableName(it) }
             && changedStatement === getTopmostStatementWithText(action.newChild, "")
    }
  }
}
