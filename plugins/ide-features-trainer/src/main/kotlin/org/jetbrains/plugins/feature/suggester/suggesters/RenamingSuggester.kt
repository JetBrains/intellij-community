package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createTipSuggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class RenamingSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use safe renaming: Shift + F6?"
        const val SUGGESTING_ACTION_ID = "Rename"
        const val SUGGESTING_TIP_FILENAME = "neue-Rename.html"
        const val NUMBER_OF_RENAMES_TO_GET_SUGGESTION = 3
    }

    private val actionsSummary = actionsLocalSummary()

    private class RenamedIdentifiersData(val initialState: String, val references: List<PsiElement>) {
        fun isAllRenamed(): Boolean {
            if (references.size < NUMBER_OF_RENAMES_TO_GET_SUGGESTION) return false
            val firstName = references[0].getIdentifiersName()
            return !(firstName == initialState || references.any { it.getIdentifiersName() != firstName })
        }

        private fun PsiElement.getIdentifiersName(): String {
            val namedElement = this as? PsiNamedElement
            return if (namedElement != null && namedElement.name != null) {
                namedElement.name!!.truncateQualifier()
            } else {
                text.truncateQualifier()
            }
        }

        private fun String.truncateQualifier(): String {
            return takeLastWhile { it != '.' }
        }
    }

    override val languages: List<String> = emptyList()

    private var renamedIdentifiersData = RenamedIdentifiersData("", emptyList())

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val action = actions.lastOrNull() ?: return NoSuggestion
        val language = action.language ?: return NoSuggestion
        val langSupport = LanguageSupport.getForLanguage(language) ?: return NoSuggestion
        val name = CommandProcessor.getInstance().currentCommandName
        if (name != null && name != "Paste") {
            return NoSuggestion
        }
        when (action) {
            is BeforeChildReplacedAction -> {
                val (parent, newChild, oldChild) = action
                if (langSupport.isIdentifier(oldChild)) {
                    if (!renamedIdentifiersData.references.contains(parent)) {
                        // TODO Find out why resolve reference causes:
                        //  "java.lang.Throwable: Somebody has requested stubbed spine during PSI operations;
                        //  not only is this expensive, but will also cause stub PSI invalidation"
                        //  Can be reproduced placing '{' before another code block "{ ... }"
                        val declaration = parent.reference?.resolve() ?: parent

                        @Suppress("SpreadOperator")
                        val references = arrayListOf(declaration, *declaration.getAllReferences().toTypedArray())
                        renamedIdentifiersData = RenamedIdentifiersData(oldChild.text, references)
                    }
                }
            }
            is ChildReplacedAction -> {
                val (parent, newChild, oldChild) = action
                if (langSupport.isIdentifier(newChild)) {
                    if (renamedIdentifiersData.references.contains(parent) &&
                        renamedIdentifiersData.isAllRenamed()
                    ) {
                        return createTipSuggestion(
                            POPUP_MESSAGE,
                            suggestingActionDisplayName,
                            SUGGESTING_TIP_FILENAME
                        )
                    }
                }
            }
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

    private fun PsiElement.getAllReferences(): List<PsiElement> {
        return ReferencesSearch.search(this).map(PsiReference::getElement)
    }

    override val id: String = "Rename all occurrences"

    override val suggestingActionDisplayName: String = "Rename all occurrences"
}
