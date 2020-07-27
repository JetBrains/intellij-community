package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getTopmostParentOfType
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildrenChangedAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class IntroduceVariableSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not use the Introduce Variable refactoring?"
        const val SUGGESTING_ACTION_ID = "IntroduceVariable"
        const val SUGGESTING_TIP_FILENAME = "neue-IntroduceVariable.html"
        const val DESCRIPTOR_ID = "refactoring.introduceVariable"
        const val MIN_NOTIFICATION_INTERVAL_DAYS = 14
    }

    private val actionsSummary = actionsLocalSummary()

    private data class ExtractedExpressionData(val exprText: String, var changedStatement: PsiElement) {
        var declaration: PsiElement? = null
        var variableEditingFinished: Boolean = false

        fun getDeclarationText(): String? {
            return declaration?.text
        }
    }

    override lateinit var langSupport: LanguageSupport

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
                    if (langSupport.isPartOfExpression(curElement) && changedStatement != null) {
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

    override fun isSuggestionNeeded(): Boolean {
        return super.isSuggestionNeeded(
            actionsSummary,
            SUGGESTING_ACTION_ID,
            TimeUnit.DAYS.toMillis(MIN_NOTIFICATION_INTERVAL_DAYS.toLong())
        )
    }

    private fun ChildReplacedAction.isVariableDeclarationAdded(): Boolean {
        return oldChild != null && newChild != null
                && langSupport.isExpressionStatement(oldChild)
                && langSupport.isVariableDeclaration(newChild)
    }

    private fun ChildAddedAction.isVariableDeclarationAdded(): Boolean {
        return parent != null && newChild != null
                && langSupport.isCodeBlock(parent)
                && langSupport.isVariableDeclaration(newChild)
    }

    private fun isVariableEditingFinished(): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            val declarationText = getDeclarationText() ?: return false
            return declarationText.trim().endsWith(exprText)
        }
    }

    private fun ChildReplacedAction.isVariableInserted(): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            return variableEditingFinished && declaration != null
                    && newChild != null && oldChild != null
                    && newChild.text == langSupport.getVariableName(declaration!!)
                    && changedStatement === newChild.getTopmostStatementWithText("")
        }
    }

    private fun PsiElement.getTopmostStatementWithText(text: String): PsiElement? {
        val statement =
            getParentByPredicate { langSupport.isSupportedStatementToIntroduceVariable(it) && it.text.contains(text) && it.text != text }
        return if (statement is KtCallExpression) {
            return statement.getTopmostParentOfType<KtDotQualifiedExpression>() ?: statement
        } else {
            statement
        }
    }

    override val suggestingActionDisplayName: String = "Introduce variable"
}