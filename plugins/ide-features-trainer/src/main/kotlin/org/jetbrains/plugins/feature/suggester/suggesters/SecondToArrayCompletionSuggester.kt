package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.codeInsight.ExpectedTypesProvider
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.cache.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsHistory
import org.jetbrains.plugins.feature.suggester.changes.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction

class SecondToArrayCompletionSuggester : FeatureSuggester {

    companion object {
        const val POPUP_MESSAGE =
            "Why not use the second Smart Completion for 'toArray' method? (double Ctrl + Shift + Space)"
        const val DESCRIPTOR_ID = "editing.completion.second.smarttype.toar"
    }

    override fun getSuggestion(actions: UserActionsHistory, anActions: UserAnActionsHistory): Suggestion {
        when (val lastAction = actions.last()) {
            is ChildAddedAction -> {
                if (isMethodCall(lastAction.newChild)) {
                    return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                }
            }
            is ChildReplacedAction -> {
                if (isMethodCall(lastAction.newChild)) {
                    return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                }
            }
        }
        return NoSuggestion
    }

    private fun isMethodCall(child: PsiElement?): Boolean {
        if (child !is PsiMethodCallExpression) {
            return false
        }
        //todo: hack not to suggest in wrong place
        if (child.argumentList.expressions.isNotEmpty()) return false
        val ref = child.methodExpression
        if (ref.referenceName != "toArray") return false
        val qualifierExpr = ref.qualifierExpression
        if (qualifierExpr !is PsiReferenceExpression || qualifierExpr.qualifierExpression != null) {
            return false
        }
        val expectedTypes = ExpectedTypesProvider.getExpectedTypes(child, true)
        if (!expectedTypes.any { it.type is PsiArrayType }) return false
        val resolve = ref.resolve()
        if (resolve is PsiMethod) {
            val clazz = resolve.containingClass ?: return false
            val collectionClass = JavaPsiFacade.getInstance(child.project)
                .findClass("java.util.Collection", GlobalSearchScope.allScope(child.project))
                ?: return false
            return clazz.isInheritor(collectionClass, true)
        }
        return false
    }

    override fun getId(): String = "Second toArray completion suggester"
}