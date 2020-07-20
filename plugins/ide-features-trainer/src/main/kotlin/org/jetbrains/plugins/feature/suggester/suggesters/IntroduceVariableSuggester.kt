package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.ide.ClipboardSynchronizer
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.PsiAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import java.awt.datatransfer.DataFlavor

/**
 * todo: this implementation is deprecated
 */
@Deprecated("Needed implementation update that will suggest in last versions")
class IntroduceVariableSuggester : FeatureSuggester {

    private data class Expression(val exprText: String, val method: PsiMethod)

    private var copiedExpression: Expression? = null

    companion object {
        const val POPUP_MESSAGE = "Why not use the Extract Variable refactoring? (Ctrl + Alt + V)"
        const val DESCRIPTOR_ID = "refactoring.introduceVariable"
    }

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val lastAction = actions.lastOrNull() ?: return NoSuggestion
        if (lastAction !is PsiAction) return NoSuggestion
        val parent = lastAction.parent
        when (lastAction) {
            is ChildAddedAction -> {
                val child = lastAction.newChild
                if (parent is PsiLocalVariable && child is PsiExpression) {
                    if (checkLocalVariable(parent, child)) {
                        return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                    }
                }
            }
            is ChildRemovedAction -> {
                val child = lastAction.child
                if (parent != null && child is PsiExpression) {
                    copiedExpression = getCopiedExpression(child) ?: copiedExpression
                }
            }
            is ChildReplacedAction -> {
                val oldChild = lastAction.oldChild
                val newChild = lastAction.newChild
                if (parent != null && newChild is PsiErrorElement && oldChild is PsiExpression) {
                    copiedExpression = getCopiedExpression(oldChild) ?: copiedExpression
                } else if (parent is PsiLocalVariable && newChild is PsiExpression) {
                    if (checkLocalVariable(parent, newChild)) {
                        return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                    }
                } else if (newChild is PsiLocalVariable && oldChild is PsiLocalVariable) {
                    if (newChild.name != oldChild.name) return NoSuggestion
                    val initializer = newChild.initializer
                    if (initializer != null && checkLocalVariable(newChild, initializer)) {
                        return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                    }
                } else if (newChild is PsiDeclarationStatement && oldChild is PsiDeclarationStatement) {
                    val newDeclaredElements = newChild.declaredElements
                    val oldDeclaredElements = oldChild.declaredElements
                    if (newDeclaredElements.size != 1 || oldDeclaredElements.size != 1) {
                        return NoSuggestion
                    }
                    val newElement = newDeclaredElements[0]
                    val oldElement = oldDeclaredElements[0]
                    if (newElement is PsiLocalVariable && oldElement is PsiLocalVariable
                        && newElement.name == oldElement.name
                    ) {
                        val initializer = newElement.initializer
                        if (initializer != null && checkLocalVariable(newElement, initializer)) {
                            return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                        }
                    }
                }
            }
            else -> return NoSuggestion
        }
        return NoSuggestion
    }

    private fun checkLocalVariable(parent: PsiLocalVariable, expr: PsiExpression): Boolean {
        if (copiedExpression != null) {
            val exprText = copiedExpression!!.exprText
            val method = copiedExpression!!.method
            if (!method.isValid) {
                copiedExpression = null
                return false
            }
            return PsiTreeUtil.isAncestor(method, parent, true)
                    && expr.text == exprText
        }
        return false
    }

    /**
     * Returns null if clipboard content is not equal expressionNode's content
     */
    private fun getCopiedExpression(expressionNode: PsiExpression): Expression? {
        try {
            val contents = ClipboardSynchronizer.getInstance().contents
            if (contents != null) {
                val clipboardContent = contents.getTransferData(DataFlavor.stringFlavor) as String
                if (clipboardContent == expressionNode.text) {
                    // let's store this action
                    val method: PsiMethod =
                        PsiTreeUtil.getParentOfType(expressionNode, PsiMethod::class.java, false)
                            ?: return null
                    return Expression(expressionNode.text, method)
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    override fun getId(): String = "Introduce variable suggester"
}