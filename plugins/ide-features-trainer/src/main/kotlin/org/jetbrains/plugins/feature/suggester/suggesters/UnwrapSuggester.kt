package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorBackspaceAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

class UnwrapSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use Unwrap action?"
        const val SUGGESTING_ACTION_ID = "Unwrap"
    }

    private var unwrappingStatements: List<PsiElement>? = null
    private val firstSelectionRegex = Regex("""[ \n]*(if|for|while)[ \n]*\(.*\)[ \n]*\{[ \n]*""")

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val lastAction = actions.lastOrNull() ?: return NoSuggestion
        when (lastAction) {
            is BeforeEditorBackspaceAction -> {
                with(lastAction) {
                    val psiFile = psiFileRef.get() ?: return NoSuggestion
                    if (selection != null) {
                        if (!selection.text.matches(firstSelectionRegex)) return NoSuggestion
                        val countStartDelimiters = selection.text.indexOfFirst { it != ' ' && it != '\n' }
                        val curElement =
                            psiFile.findElementAt(selection.startOffset + countStartDelimiters) ?: return NoSuggestion
                        val parent = curElement.parent ?: return NoSuggestion
                        if (parent.isSurroundingStatement()) {
                            val codeBlock = parent.getCodeBlock() ?: return NoSuggestion
                            unwrappingStatements = codeBlock.getStatements()
                        }
                    } else if (unwrappingStatements != null) {
                        val curElement = psiFile.findElementAt(caretOffset - 1) ?: return NoSuggestion
                        if (curElement.text != "}") return NoSuggestion
                        val codeBlock = curElement.getContainingCodeBlock() ?: return NoSuggestion
                        val statements = codeBlock.getStatements()
                        if (intersectsByText(unwrappingStatements!!, statements)) {
                            unwrappingStatements = null
                            return createSuggestion(
                                null,
                                createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                                suggestingActionDisplayName,
                                ""
                            )
                        }
                        unwrappingStatements = null
                    } else {
                        unwrappingStatements = null
                    }
                }
            }
            else -> NoSuggestion
        }
        return NoSuggestion
    }

    private fun intersectsByText(unwrappingStatements: List<PsiElement>, blockStatements: List<PsiElement>): Boolean {
        if (unwrappingStatements.isEmpty() || unwrappingStatements.size > blockStatements.size) return false
        val first = unwrappingStatements[0]
        var index = blockStatements.indexOfFirst { it.text == first.text }
        if (index == -1) return false
        return unwrappingStatements.all {
            if (index < blockStatements.size) {
                it.text == blockStatements[index++].text
            } else {
                false
            }
        }
    }

    /**
     * @receiver need to be a code block @see [getContainingCodeBlock] [getCodeBlock]
     */
    private fun PsiElement.getStatements(): List<PsiElement> {
        return if (this is PsiCodeBlock) {
            statements.toList()
        } else {
            val statements = children.toList()
            if (statements.any { it !is KtExpression }) {
                emptyList()
            } else {
                statements
            }
        }
    }

    private fun PsiElement.getCodeBlock(): PsiElement? {
        return findDescendantOfType<PsiCodeBlock>()
            ?: findDescendantOfType<KtBlockExpression>()
    }

    private fun PsiElement.getContainingCodeBlock(): PsiElement? {
        return getParentOfType<PsiCodeBlock>()
            ?: getParentOfType<KtBlockExpression>()
    }

    private fun PsiElement.isSurroundingStatement(): Boolean {
        return this is PsiIfStatement || this is PsiForStatement || this is PsiWhileStatement
                || this is KtIfExpression || this is KtForExpression || this is KtWhileExpression
    }

    override val suggestingActionDisplayName: String = "Unwrap"
}