package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.*
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

class IntroduceVariableSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not use the Introduce Variable refactoring?"
        const val SUGGESTING_ACTION_ID = "IntroduceVariable"
        const val SUGGESTING_TIP_FILENAME = "neue-IntroduceVariable.html"
        const val DESCRIPTOR_ID = "refactoring.introduceVariable"
    }

    private data class ExtractedExpressionData(val exprText: String, var changedStatement: PsiStatement) {
        var declaration: PsiDeclarationStatement? = null
        var variableEditingFinished: Boolean = false

        fun getDeclarationName(): String? {
            if (declaration == null) return null
            val localVariable = declaration!!.declaredElements.firstOrNull() as? PsiLocalVariable ?: return null
            return localVariable.name
        }

        fun getLocalVariable(): PsiLocalVariable? {
            return declaration?.declaredElements?.firstOrNull() as? PsiLocalVariable
        }
    }

    private var extractedExprData: ExtractedExpressionData? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val lastAction = actions.lastOrNull() ?: return NoSuggestion
        when (lastAction) {
            is BeforeEditorTextRemovedAction -> {
                with(lastAction) {
                    if (text.trim() != CopyPasteManager.getInstance().contents?.asString()?.trim()) {
                        return NoSuggestion
                    }
                    val psiFile = psiFileRef.get() ?: return NoSuggestion
                    val countOfStartDelimiters = text.indexOfFirst { it != ' ' && it != '\n' }
                    val curElement =
                        psiFile.findElementAt(offset + countOfStartDelimiters) ?: return NoSuggestion
                    val expr = curElement.getParentOfType<PsiExpression>()
                    val changedStatement = curElement.getParentOfType<PsiStatement>()
                    if (expr != null && changedStatement != null) {
                        extractedExprData = ExtractedExpressionData(text.trim(), changedStatement)
                    }
                }
            }
            is ChildReplacedAction -> {
                if (extractedExprData == null) return NoSuggestion
                with(lastAction) {
                    if (oldChild is PsiExpressionStatement && newChild is PsiDeclarationStatement) {
                        extractedExprData!!.declaration = newChild
                    } else if (isVariableEditingFinished()) {
                        extractedExprData!!.variableEditingFinished = true
                    } else if (isVariableInserted()) {
                        extractedExprData = null
                        return createSuggestion(
                            DESCRIPTOR_ID,
                            createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                            getId(),
                            SUGGESTING_TIP_FILENAME
                        )
                    }
                }
            }
            is ChildAddedAction -> {
                if (extractedExprData == null) return NoSuggestion
                with(lastAction) {
                    if (parent is PsiCodeBlock && newChild is PsiDeclarationStatement) {
                        extractedExprData!!.declaration = newChild
                    } else if (newChild is PsiStatement && newChild.text == extractedExprData!!.changedStatement.text) {
                        extractedExprData!!.changedStatement = newChild
                    }
                }
            }
            else -> NoSuggestion
        }
        return NoSuggestion
    }

    private fun ChildReplacedAction.isVariableEditingFinished(): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            val variable = getLocalVariable() ?: return false
            return declaration != null && parent === variable
                    && newChild is PsiJavaToken && newChild.text == ";"
                    && oldChild is PsiErrorElement && oldChild.errorDescription == "';' expected"
                    && variable.getInitializationExpr()?.text == exprText
        }
    }

    private fun ChildReplacedAction.isVariableInserted(): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            return variableEditingFinished
                    && oldChild is PsiIdentifier && newChild is PsiIdentifier
                    && changedStatement === newChild.getParentOfType<PsiStatement>()
                    && newChild.text == getDeclarationName()
        }
    }

    private fun PsiLocalVariable.getInitializationExpr(): PsiExpression? {
        val elements = children
        if (elements.size < 2) return null
        return elements[elements.size - 2] as? PsiExpression
    }

    override fun getId(): String = "Introduce variable suggester"
}