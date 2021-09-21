package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.feature.suggester.FeatureSuggesterBundle
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildrenChangedAction
import org.jetbrains.plugins.feature.suggester.actions.EditorTextInsertedAction
import org.jetbrains.plugins.feature.suggester.actions.PsiAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport

class SurroundWithSuggester : AbstractFeatureSuggester() {
    override val id: String = "Surround with"
    override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("surround.with.name")

    override val message = FeatureSuggesterBundle.message("surround.with.message")
    override val suggestingActionId = "SurroundWith"
    override val suggestingTipFileName = "SurroundWith.html"

    override val languages = listOf("JAVA", "kotlin")

    private object State {
        var surroundingStatement: PsiElement? = null
        var surroundingStatementStartOffset: Int = -1
        var firstStatementInBlockText: String = ""
        var isLeftBraceAdded: Boolean = false
        var isRightBraceAdded: Boolean = false
        var lastChangeTimeMillis: Long = 0L

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
            } else {
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
        }
    }

    @Suppress("NestedBlockDepth")
    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val action = actions.lastOrNull() ?: return NoSuggestion
        val language = action.language ?: return NoSuggestion
        val langSupport = LanguageSupport.getForLanguage(language) ?: return NoSuggestion
        if (State.surroundingStatement?.isValid == false && action is PsiAction) {
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
                val textInsertedAction = actions.findLastTextInsertedAction() ?: return NoSuggestion
                val text = textInsertedAction.text
                if (text.contains("{") || text.contains("}")) {
                    val psiFile = action.parent as? PsiFile ?: return NoSuggestion
                    if (State.isOutOfDate(action.timeMillis) || text != "{" && text != "}") {
                        State.reset()
                    } else if (text == "{") {
                        if (State.isLeftBraceAdded) {
                            State.reset()
                        } else if (State.isBraceAddedToStatement(
                                langSupport,
                                psiFile,
                                textInsertedAction.caretOffset
                            )
                        ) {
                            State.applyBraceAddition(action.timeMillis, "{")
                            State.saveFirstStatementInBlock(langSupport)
                        }
                    } else if (text == "}") {
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
            else -> NoSuggestion
        }
        return NoSuggestion
    }

    private fun State.tryToUpdateSurroundingStatement(langSupport: LanguageSupport, action: PsiAction) {
        val element = action.psiFile.findElementAt(surroundingStatementStartOffset) ?: return
        val parent = element.parent ?: return
        if (langSupport.isSurroundingStatement(parent)) {
            surroundingStatement = parent
        }
    }

    private fun UserActionsHistory.findLastTextInsertedAction(): EditorTextInsertedAction? {
        return asIterable().findLast { it is EditorTextInsertedAction } as? EditorTextInsertedAction
    }

    private fun State.isBraceAddedToStatement(langSupport: LanguageSupport, psiFile: PsiFile, offset: Int): Boolean {
        val curElement = psiFile.findElementAt(offset) ?: return false
        return curElement.parent === langSupport.getCodeBlock(surroundingStatement!!)
    }

    private fun State.saveFirstStatementInBlock(langSupport: LanguageSupport) {
        val statements = langSupport.getStatementsOfBlock(surroundingStatement!!)
        if (statements.isNotEmpty()) {
            firstStatementInBlockText = statements.first().text
        }
    }

    private fun State.isStatementsSurrounded(langSupport: LanguageSupport): Boolean {
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

    private fun LanguageSupport.getStatementsOfBlock(psiElement: PsiElement): List<PsiElement> {
        val codeBlock = getCodeBlock(psiElement)
        return if (codeBlock != null) {
            getStatements(codeBlock)
        } else {
            emptyList()
        }
    }

    private fun LanguageSupport.isSurroundingStatement(psiElement: PsiElement): Boolean {
        return isIfStatement(psiElement) || isForStatement(psiElement) || isWhileStatement(psiElement)
    }

    companion object {
        const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 8000L
    }
}
