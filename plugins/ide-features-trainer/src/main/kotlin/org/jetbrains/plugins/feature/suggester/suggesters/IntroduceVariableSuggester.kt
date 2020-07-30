package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getTopmostParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildrenChangedAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

class IntroduceVariableSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not use the Introduce Variable refactoring?"
        const val SUGGESTING_ACTION_ID = "IntroduceVariable"
        const val SUGGESTING_TIP_FILENAME = "neue-IntroduceVariable.html"
        const val DESCRIPTOR_ID = "refactoring.introduceVariable"
    }

    private data class ExtractedExpressionData(val exprText: String, var changedStatement: PsiElement) {
        var declaration: PsiElement? = null
        var variableEditingFinished: Boolean = false

        fun getDeclarationInitializationText(): String? {
            return declaration?.children?.firstOrNull()?.text
        }
    }

    private var extractedExprData: ExtractedExpressionData? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val lastAction = actions.lastOrNull() ?: return NoSuggestion
        when (lastAction) {
            is BeforeEditorTextRemovedAction -> {
                with(lastAction) {
                    if (text.isBlank()) return NoSuggestion
                    val deletedText = text.trim()
                    if (deletedText != CopyPasteManager.getInstance().contents?.asString()?.trim()) {
                        return NoSuggestion
                    }
                    val psiFile = psiFileRef.get() ?: return NoSuggestion
                    val countOfStartDelimiters = text.indexOfFirst { it != ' ' && it != '\n' }
                    val curElement =
                        psiFile.findElementAt(offset + countOfStartDelimiters) ?: return NoSuggestion
                    val changedStatement = curElement.getTopmostStatementWithText(deletedText)
                    if (curElement.isPartOfExpression() && changedStatement != null) {
                        extractedExprData = ExtractedExpressionData(deletedText, changedStatement)
                    }
                }
            }
            is ChildReplacedAction -> {
                if (extractedExprData == null) return NoSuggestion
                with(lastAction) {
                    if (isVariableDeclarationAdded()) {
                        extractedExprData!!.declaration = newChild
                    } else if (isVariableInserted()) {
                        extractedExprData = null
                        return createSuggestion(
                            DESCRIPTOR_ID,
                            createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                            suggestingActionDisplayName,
                            SUGGESTING_TIP_FILENAME
                        )
                    }
                }
            }
            is ChildAddedAction -> {
                if (extractedExprData == null) return NoSuggestion
                with(lastAction) {
                    if (isVariableDeclarationAdded()) {
                        extractedExprData!!.declaration = newChild
                    } else if (newChild != null && newChild.text == extractedExprData!!.changedStatement.text) {
                        extractedExprData!!.changedStatement = newChild
                    } else if (!extractedExprData!!.variableEditingFinished && isVariableEditingFinished()) {
                        extractedExprData!!.variableEditingFinished = true
                    }
                }
            }
            is ChildrenChangedAction -> {
                if (extractedExprData == null) return NoSuggestion
                val parent = lastAction.parent ?: return NoSuggestion
                if (parent === extractedExprData!!.declaration
                    && !extractedExprData!!.variableEditingFinished
                    && isVariableEditingFinished()
                ) {
                    extractedExprData!!.variableEditingFinished = true
                }
            }
            else -> NoSuggestion
        }
        return NoSuggestion
    }

    private fun ChildReplacedAction.isVariableDeclarationAdded(): Boolean {
        return oldChild is PsiExpressionStatement && newChild is PsiDeclarationStatement
                || oldChild is KtExpression && newChild is KtProperty
    }

    private fun ChildAddedAction.isVariableDeclarationAdded(): Boolean {
        return parent is PsiCodeBlock && newChild is PsiDeclarationStatement
                || parent is KtBlockExpression && newChild is KtProperty
    }

    private fun isVariableEditingFinished(): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            val declarationText = getDeclarationInitializationText() ?: return false
            return declarationText.trim().endsWith(exprText)
        }
    }

    private fun ChildReplacedAction.isVariableInserted(): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            return variableEditingFinished
                    && newChild != null && oldChild != null
                    && newChild.text == declaration?.getVariableName()
                    && changedStatement === newChild.getTopmostStatementWithText("")
        }
    }

    private fun PsiElement.getTopmostStatementWithText(text: String): PsiElement? {
        val statement = getParentByPredicate { isSupportedStatement(it) && it.text.contains(text) && it.text != text }
        return if (statement is KtCallExpression) {
            return statement.getTopmostParentOfType<KtDotQualifiedExpression>() ?: statement
        } else {
            statement
        }
    }

    private fun PsiElement.getVariableName(): String? {
        if (this is PsiDeclarationStatement) {
            val localVariable = declaredElements.lastOrNull() as? PsiLocalVariable ?: return null
            return localVariable.name
        } else if (this is KtProperty) {
            return name
        }
        return null
    }

    private fun PsiElement.isPartOfExpression(): Boolean {
        return getParentOfType<PsiExpression>() != null || getParentOfType<KtExpression>() != null
    }

    private fun PsiElement.getParentByPredicate(predicate: (PsiElement) -> Boolean): PsiElement? {
        return parents.find(predicate)
    }

    private fun isSupportedStatement(element: PsiElement): Boolean {
        return element is PsiStatement || element is KtProperty || element is KtIfExpression
                || element is KtCallExpression || element is KtQualifiedExpression
                || element is KtReturnExpression
    }

    override val suggestingActionDisplayName: String = "Introduce variable"
}