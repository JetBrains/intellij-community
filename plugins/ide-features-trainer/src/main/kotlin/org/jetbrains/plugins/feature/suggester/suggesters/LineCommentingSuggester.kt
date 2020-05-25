package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.cache.UserActionsCache
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsCache
import org.jetbrains.plugins.feature.suggester.changes.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildRemovedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.changes.UserAction

class LineCommentingSuggester : FeatureSuggester {

    val POPUP_MESSAGE = "Try the Comment Line feature to do it faster (Ctrl + /)"
    val UNCOMMENTING_POPUP_MESSAGE = "Why not use the Uncomment Line feature? (Ctrl + /)"
    val DESCRIPTOR_ID = "codeassists.comment.line"

    private var uncommentingActionStart: UserAction? = null

    override fun getSuggestion(actions: UserActionsCache, anActions: UserAnActionsCache): Suggestion {
        val name = CommandProcessor.getInstance().currentCommandName
        if (name != null) {
            return NoSuggestion
        }

        when (val lastAction = actions.last()) {
            is ChildAddedAction -> {
                val child = lastAction.newChild
                if (child != null && isOneLineComment(child)
                    && isCommentAddedToLineStart(child.containingFile, child.textRange.startOffset)
                ) {
                    return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                }
            }
            is ChildReplacedAction -> {
                val newChild = lastAction.newChild
                val oldChild = lastAction.oldChild

                if(newChild == null || oldChild == null) {
                    return NoSuggestion
                }

                if (isOneLineComment(newChild)
                    && oldChild !is PsiComment
                    && isCommentAddedToLineStart(newChild.containingFile, newChild.textRange.startOffset)
                ) {
                    return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                } else if (isOneLineComment(oldChild)
                    && newChild !is PsiComment
                    && isCommentAddedToLineStart(newChild.containingFile, newChild.textRange.startOffset)
                ) {
                    val suggestion = createSuggestion(DESCRIPTOR_ID, UNCOMMENTING_POPUP_MESSAGE)
                    if (suggestion is PopupSuggestion) {
                        uncommentingActionStart = lastAction
                    }
                    return suggestion
                }
            }
            is ChildRemovedAction -> {
                val child = lastAction.child
                if (child is PsiErrorElement
                    && child.text == "/"
                    && uncommentingActionStart != null
                    && actions.contains(uncommentingActionStart!!)
                ) {
                    uncommentingActionStart = null
                    return createSuggestion(DESCRIPTOR_ID, UNCOMMENTING_POPUP_MESSAGE)
                }
            }
            else -> return NoSuggestion
        }
        return NoSuggestion
    }

    override fun getId(): String = "Commenting suggester"
}