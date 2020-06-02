package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.codeInsight.ExpectedTypesProvider
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.cache.UserActionsCache
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsCache
import org.jetbrains.plugins.feature.suggester.changes.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction

class SecondToArrayCompletionSuggester : FeatureSuggester {

    companion object {
        val POPUP_MESSAGE =
            "Why not use the second Smart Completion for 'toArray' method? (double Ctrl + Shift + Space)"
        val DESCRIPTOR_ID = "editing.completion.second.smarttype.toar"
    }

    override fun getSuggestion(actions: UserActionsCache, anActions: UserAnActionsCache): Suggestion {
        val lastAction = actions.last()
        val parent = lastAction.parent
        when (lastAction) {
            is ChildAddedAction -> {
                val child = lastAction.newChild
                if (child is PsiMethodCallExpression && checkMethodCall(child)) {
                    return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                }
            }
            is ChildReplacedAction -> {
                val newChild = lastAction.newChild
                if(newChild is PsiMethodCallExpression && checkMethodCall(newChild)) {
                    return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                }
            }
        }
        return NoSuggestion
    }

    private fun checkMethodCall(call: PsiMethodCallExpression): Boolean {
        //todo: hack not to suggest in wrong place
        if(call.argumentList.expressions.isNotEmpty()) return false
        val ref = call.methodExpression
        if(ref.referenceName != "toArray") return false
        val qualifierExpr = ref.qualifierExpression
        if(qualifierExpr !is PsiReferenceExpression || qualifierExpr.qualifierExpression != null) {
            return false
        }
        val expectedTypes = ExpectedTypesProvider.getExpectedTypes(call, true)
        if(!expectedTypes.any { it.type is PsiArrayType }) return false
        val resolve = ref.resolve()
        if(resolve is PsiMethod) {
            val clazz = resolve.containingClass ?: return false
            val collectionClass = JavaPsiFacade.getInstance(call.project)
                .findClass("java.util.Collection", GlobalSearchScope.allScope(call.project))
                ?: return false
            return clazz.isInheritor(collectionClass, true)
        }
        return false
    }

    override fun getId(): String = "Second toArray completion suggester"
}