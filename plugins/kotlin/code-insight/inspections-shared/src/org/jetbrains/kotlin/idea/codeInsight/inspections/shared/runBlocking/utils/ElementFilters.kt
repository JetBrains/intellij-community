package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.utils

import com.intellij.psi.util.PsiElementFilter
import org.jetbrains.kotlin.idea.base.psi.hasInlineModifier
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.hasSuspendModifier

/**
 * Custom PsiElementFilters used for runBlocking inspections
 */
internal class ElementFilters {
    companion object {
        val runBlockingBuilderInvocation = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "runBlocking") {
                        val funDef = callee.reference?.resolve()
                        return@PsiElementFilter funDef?.let { runBlockingBuilderDeclaration.isAccepted(it) } == true
                    }
                }
            }
            false
        }
        
        val suspendFun = PsiElementFilter { el -> 
            if (el is KtNamedFunction) {
                return@PsiElementFilter el.modifierList?.hasSuspendModifier() ?: false
            }
            return@PsiElementFilter false
        }
        
        val launchBuilder = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "launch") {
                        val funDef = callee.reference?.resolve()
                        return@PsiElementFilter funDef?.let { launchBuilderDeclaration.isAccepted(it) } == true
                    }
                }
            }
            false
        }
        
        val asyncBuilder = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression){
                    if (callee.getReferencedName() == "async") {
                        val funDef = callee.reference?.resolve()
                        return@PsiElementFilter funDef?.let { asyncBuilderDeclaration.isAccepted(it) } == true
                    }
                }
            }
            false
        }
        
        val inlineFunctionCall = PsiElementFilter { el ->
            if (el is KtCallExpression) {
                val callee = el.calleeExpression
                if (callee is KtNameReferenceExpression) {
                    val funDef = callee.reference?.resolve()
                    if (funDef is KtNamedFunction) {
                        return@PsiElementFilter funDef.hasInlineModifier()
                    }
                }
            }
            false
        }
        
        val lambdaAsArgForInlineFun = PsiElementFilter { el ->
            if (el is KtLambdaExpression) {
                val funCall = MyPsiUtils.findParent(el, { it is KtCallExpression }, {false})?: return@PsiElementFilter false
                return@PsiElementFilter inlineFunctionCall.isAccepted(funCall)
            }
            false
        }
        
        
        val isSuspendLambda = PsiElementFilter { el ->
            if (el is KtLambdaExpression) {
                if (el.functionLiteral.modifierList?.hasSuspendModifier() == true) 
                    return@PsiElementFilter true
                val funCall = (MyPsiUtils.findParent(el, { it is KtCallExpression }, {false})?: return@PsiElementFilter false) as KtCallExpression
                val callee = funCall.calleeExpression
                if (callee is KtNameReferenceExpression) {
                    val funDef = callee.reference?.resolve()
                    if (funDef is KtNamedFunction) {
                        funDef.valueParameters.forEach {parameter -> 
                            if (parameter.typeReference?.modifierList?.hasSuspendModifier() == true) return@PsiElementFilter true
                        }
                    }
                }
            }
            false
        }
        
        val runBlockingBuilderDeclaration = PsiElementFilter { it is KtNamedFunction && it.fqName?.toString() == "kotlinx.coroutines.runBlocking" }
        val launchBuilderDeclaration = PsiElementFilter { it is KtNamedFunction && it.fqName?.toString() == "kotlinx.coroutines.launch" }
        val asyncBuilderDeclaration = PsiElementFilter { it is KtNamedFunction && it.fqName?.toString() == "kotlinx.coroutines.async" }
    }
}