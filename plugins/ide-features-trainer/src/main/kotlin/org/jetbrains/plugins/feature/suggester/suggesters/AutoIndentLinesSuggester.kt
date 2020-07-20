package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

class AutoIndentLinesSuggester : FeatureSuggester {

    companion object {
        const val POPUP_MESSAGE = "Why not use the Auto-Indent feature? (Ctrl + Shift + I)"
    }

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val command = CommandProcessor.getInstance().currentCommand
        if (command != null) return NoSuggestion
        val lastAction = actions.lastOrNull()
        if (lastAction is ChildReplacedAction) {
            val newChild = lastAction.newChild
            val oldChild = lastAction.oldChild
            if (newChild is PsiWhiteSpace && oldChild is PsiWhiteSpace) {
                val newChildText = newChild.text
                val oldChildText = oldChild.text
                if (newChildText.contains('\n')
                    && newChildText.count { it == '\n' } == oldChildText.count { it == '\n' }
                    && newChildText.takeLastWhile { it != '\n' }.length != oldChildText.takeLastWhile { it != '\n' }.length
                    && newChild.nextSibling != null && newChild.nextSibling !is PsiWhiteSpace
                ) {
                    return createSuggestion(null, POPUP_MESSAGE)
                }
            }
        }
        return NoSuggestion
    }

    override fun getId(): String = "AutoIndent lines suggester"
}