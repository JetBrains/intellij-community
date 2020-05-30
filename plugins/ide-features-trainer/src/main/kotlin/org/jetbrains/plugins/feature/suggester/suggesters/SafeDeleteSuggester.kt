package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.cache.UserActionsCache
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsCache
import org.jetbrains.plugins.feature.suggester.changes.ChildRemovedAction

class SafeDeleteSuggester : FeatureSuggester {
    companion object {
        val POPUP_MESSAGE = "Just to be on the safe side, try Safe Delete instead (Alt + Delete)"
        val suggestionCountdownMillis = 150
    }

    private var lastTimeForPopupMillis = 0L

    /**
     * Returns PopupSuggestion when user removing variable/field/method/class declaration
     * (CHANGED FROM LAST VERSION)
     */
    override fun getSuggestion(actions: UserActionsCache, anActions: UserAnActionsCache): Suggestion {
        val name = CommandProcessor.getInstance().currentCommandName
        if (name != null) {
            //it's not user typing action, so let's do nothing
            return NoSuggestion
        }
        val lastAction = actions.last()
        if (lastAction is ChildRemovedAction && isDeclarationRemoving(lastAction)) {
            val delta = System.currentTimeMillis() - lastTimeForPopupMillis
            if (delta < suggestionCountdownMillis) {
                IdeTooltipManager.getInstance().hideCurrentNow(false)
                return NoSuggestion //do not suggest in case of deletion for few things
            }
            lastTimeForPopupMillis = System.currentTimeMillis()
            return createSuggestion(null, POPUP_MESSAGE)
        }
        return NoSuggestion
    }

    private fun isDeclarationRemoving(action: ChildRemovedAction): Boolean {
        val parent = action.parent
        val child = action.child
        if (parent != null && child != null) {
            return child is PsiDeclarationStatement
                    || child is PsiField
                    || child is PsiMethod
                    || child is PsiClass
        }
        return false
    }

    override fun getId(): String = "Safe delete suggester"
}