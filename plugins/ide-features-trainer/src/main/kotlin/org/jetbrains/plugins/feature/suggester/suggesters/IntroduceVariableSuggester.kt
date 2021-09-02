package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getTopmostParentOfType
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildrenChangedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.asString
import org.jetbrains.plugins.feature.suggester.createTipSuggestion
import org.jetbrains.plugins.feature.suggester.getParentByPredicate
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.awt.datatransfer.DataFlavor
import java.util.concurrent.TimeUnit

class IntroduceVariableSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not use the Introduce Variable refactoring?"
        const val SUGGESTING_ACTION_ID = "IntroduceVariable"
        const val SUGGESTING_TIP_FILENAME = "neue-IntroduceVariable.html"
    }

    private val actionsSummary = actionsLocalSummary()

    private data class ExtractedExpressionData(var exprText: String, var changedStatement: PsiElement) {
        val changedStatementText: String
        var declaration: PsiElement? = null
        var variableEditingFinished: Boolean = false

        init {
            val text = changedStatement.text
            changedStatementText = text.replaceFirst(exprText, "").trim()
            exprText = exprText.trim()
        }

        fun getDeclarationText(): String? {
            return declaration?.text
        }
    }

    override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

    private var extractedExprData: ExtractedExpressionData? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val action = actions.lastOrNull() ?: return NoSuggestion
        val language = action.language ?: return NoSuggestion
        val langSupport = LanguageSupport.getForLanguage(language) ?: return NoSuggestion
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
                            return createTipSuggestion(
                                createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                                suggestingActionDisplayName,
                                SUGGESTING_TIP_FILENAME
                            )
                        }
                    }
                }
            }
            is ChildAddedAction -> {
                if (extractedExprData == null) return NoSuggestion
                with(action) {
                    if (langSupport.isVariableDeclarationAdded(this)) {
                        extractedExprData!!.declaration = newChild
                    } else if (newChild.text.trim() == extractedExprData!!.changedStatementText) {
                        extractedExprData!!.changedStatement = newChild
                    } else if (!extractedExprData!!.variableEditingFinished && isVariableEditingFinished()) {
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

    override fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean {
        return super.isSuggestionNeeded(
            actionsSummary,
            SUGGESTING_ACTION_ID,
            TimeUnit.DAYS.toMillis(minNotificationIntervalDays.toLong())
        )
    }

    private fun getCopiedContent(text: String): String? {
        if (text.isBlank()) return null
        val content = text.trim()
        val copyPasteManager = CopyPasteManager.getInstance()
        return if (copyPasteManager.areDataFlavorsAvailable(DataFlavor.stringFlavor) &&
            content == copyPasteManager.contents?.asString()?.trim()
        ) {
            content
        } else {
            null
        }
    }

    private fun LanguageSupport.isVariableDeclarationAdded(action: ChildReplacedAction): Boolean {
        return isExpressionStatement(action.oldChild) && isVariableDeclaration(action.newChild)
    }

    private fun LanguageSupport.isVariableDeclarationAdded(action: ChildAddedAction): Boolean {
        return isCodeBlock(action.parent) && isVariableDeclaration(action.newChild)
    }

    private fun isVariableEditingFinished(): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            val declarationText = getDeclarationText() ?: return false
            return declarationText.trim().endsWith(exprText)
        }
    }

    private fun LanguageSupport.isVariableInserted(action: ChildReplacedAction): Boolean {
        if (extractedExprData == null) return false
        with(extractedExprData!!) {
            return variableEditingFinished && declaration != null &&
                    action.newChild.text == getVariableName(declaration!!) &&
                    changedStatement === getTopmostStatementWithText(action.newChild, "")
        }
    }

    private fun LanguageSupport.getTopmostStatementWithText(psiElement: PsiElement, text: String): PsiElement? {
        val statement = psiElement.getParentByPredicate {
            isSupportedStatementToIntroduceVariable(it) && it.text.contains(text) && it.text != text
        }
        return if (statement is KtCallExpression) {
            return statement.getTopmostParentOfType<KtDotQualifiedExpression>() ?: statement
        } else {
            statement
        }
    }

    override val id: String = "Introduce variable"

    override val suggestingActionDisplayName: String = "Introduce variable"
}
