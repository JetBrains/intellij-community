package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiIdentifier
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.cache.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsHistory
import org.jetbrains.plugins.feature.suggester.changes.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction

class SuppressWarningSuggester : FeatureSuggester {

    companion object {
        const val POPUP_MESSAGE = "Why not use quickfix for inspection to suppress it? (Alt + Enter)"
    }

    override fun getSuggestion(actions: UserActionsHistory, anActions: UserAnActionsHistory): Suggestion {
        when (val lastAction = actions.last()) {
            is ChildAddedAction -> {
                val child = lastAction.newChild
                if (child is PsiAnnotation && child.text.startsWith("@SuppressWarnings")) {
                    return createSuggestion(null, POPUP_MESSAGE)
                } else if (child is PsiIdentifier && child.text == "SuppressWarnings") {
                    return checkIdentifier(child)
                }
            }
            is ChildReplacedAction -> {
                val newChild = lastAction.newChild
                val oldChild = lastAction.oldChild
                if (newChild is PsiComment && newChild.text.startsWith("//noinspection")) {
                    if (oldChild is PsiComment && oldChild.text.startsWith("//noinspection")) {
                        return NoSuggestion
                    }
                    if (isCommentAddedToLineStart(newChild.containingFile, newChild.textRange.startOffset)) {
                        return createSuggestion(null, POPUP_MESSAGE)
                    }
                } else if (newChild is PsiAnnotation && newChild.text.startsWith("@SuppressWarnings")) {
                    if (oldChild !is PsiAnnotation || !oldChild.text.startsWith("@SuppressWarnings")) {
                        return createSuggestion(null, POPUP_MESSAGE)
                    }
                } else if (newChild is PsiIdentifier && newChild.text.startsWith("SuppressWarnings")) {
                    return checkIdentifier(newChild)
                }
            }
        }
        return NoSuggestion
    }

    private fun checkIdentifier(child: PsiIdentifier): Suggestion {
        val parent = child.parent
        if (parent is PsiAnnotation || parent != null && parent.parent is PsiAnnotation) {
            return createSuggestion(null, POPUP_MESSAGE)
        }
        return NoSuggestion
    }

    override fun getId(): String = "Suppress warning suggester"
}