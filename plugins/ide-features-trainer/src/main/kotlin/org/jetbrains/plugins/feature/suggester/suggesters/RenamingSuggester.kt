package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.feature.suggester.FeatureSuggesterBundle
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport

class RenamingSuggester : AbstractFeatureSuggester() {
    override val id: String = "Rename all occurrences"
    override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("rename.name")

    override val message = FeatureSuggesterBundle.message("rename.message")
    override val suggestingActionId = "Rename"
    override val suggestingTipFileName = "Rename.html"

    override val languages: List<String> = emptyList()

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

    private var renamedIdentifiersData = RenamedIdentifiersData("", emptyList())

    override fun getSuggestion(action: Action): Suggestion {
        val language = action.language ?: return NoSuggestion
        val langSupport = LanguageSupport.getForLanguage(language) ?: return NoSuggestion
        val name = CommandProcessor.getInstance().currentCommandName
        if (name != null && name != "Paste") {
            return NoSuggestion
        }
        when (action) {
            is BeforeChildReplacedAction -> {
                val parent = action.parent
                val oldChild = action.oldChild
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
                val parent = action.parent
                val newChild = action.newChild
                if (langSupport.isIdentifier(newChild)) {
                    if (renamedIdentifiersData.references.contains(parent) &&
                        renamedIdentifiersData.isAllRenamed()
                    ) {
                        return createSuggestion()
                    }
                }
            }
        }
        return NoSuggestion
    }

    private fun PsiElement.getAllReferences(): List<PsiElement> {
        return ReferencesSearch.search(this).map(PsiReference::getElement)
    }

    companion object {
        const val NUMBER_OF_RENAMES_TO_GET_SUGGESTION = 3
    }
}
