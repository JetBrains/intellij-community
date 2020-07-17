package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.*
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.changes.BeforeEditorBackspaceAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.history.UserAnActionsHistory

class UnwrapSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use Unwrap action: Ctrl + Shift + Delete?"
    }

    private var unwrappingStatements: List<PsiElement>? = null
    private val firstSelectionRegex = Regex("""[ \n]*(if|for|while)[ \n]*\(.*\)[ \n]*\{[ \n]*""")

    override fun getSuggestion(actions: UserActionsHistory, anActions: UserAnActionsHistory): Suggestion {
        val lastAction = anActions.lastOrNull() ?: return NoSuggestion
        when (lastAction) {
            is BeforeEditorBackspaceAction -> {
                with(lastAction) {
                    val psiFile = psiFileRef.get() ?: return NoSuggestion
                    if (selection != null) {
                        if (!selection.text.matches(firstSelectionRegex)) return NoSuggestion
                        val countStartDelimiters = selection.text.indexOfFirst { it != ' ' && it != '\n' }
                        val curElement = psiFile.findElementAt(selection.startOffset + countStartDelimiters) ?: return NoSuggestion
                        val parent = curElement.parent ?: return NoSuggestion
                        if (parent.isSurroundingStatement()) {
                            unwrappingStatements =
                                parent.children.lastOrNull()?.children?.lastOrNull()?.children?.filterIsInstance<PsiStatement>()
                                    ?: return NoSuggestion
                        }
                    } else if (unwrappingStatements != null) {
                        val curElement = psiFile.findElementAt(caretOffset - 1) ?: return NoSuggestion
                        if (curElement.text != "}") return NoSuggestion
                        val codeBlock = curElement.getParentOfType<PsiCodeBlock>() ?: return NoSuggestion
                        val statements = codeBlock.statements.toList()
                        if (intersectsByText(unwrappingStatements!!, statements)) {
                            unwrappingStatements = null
                            return createSuggestion(null, POPUP_MESSAGE)
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
        return unwrappingStatements.all {
            if (index < blockStatements.size) {
                it.text == blockStatements[index++].text
            } else {
                false
            }
        }
    }

    private fun PsiElement.isSurroundingStatement(): Boolean {
        return this is PsiIfStatement || this is PsiForStatement || this is PsiWhileStatement
    }

    override fun getId(): String = "Unwrap suggester"
}