// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.replaceWithCopyWithResolveCheck
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

class RedundantLambdaArrowInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return lambdaExpressionVisitor(fun(lambdaExpression: KtLambdaExpression) {
            val functionLiteral = lambdaExpression.functionLiteral
            val arrow = functionLiteral.arrow ?: return
            val parameters = functionLiteral.valueParameters
            val singleParameter = parameters.singleOrNull()
            if (singleParameter?.typeReference != null) return
            if (parameters.isNotEmpty() && singleParameter?.name != "it") {
                return
            }

            if (lambdaExpression.getStrictParentOfType<KtWhenEntry>()?.expression == lambdaExpression) return
            if (lambdaExpression.getStrictParentOfType<KtContainerNodeForControlStructureBody>()?.expression == lambdaExpression) return

            val callExpression = lambdaExpression.parent?.parent as? KtCallExpression
            if (callExpression != null) {
                val callee = callExpression.calleeExpression as? KtNameReferenceExpression
                if (callee != null && callee.getReferencedName() == "forEach" && singleParameter?.name != "it") return
            }

            val lambdaContext = lambdaExpression.analyze()
            if (parameters.isNotEmpty() && lambdaContext[BindingContext.EXPECTED_EXPRESSION_TYPE, lambdaExpression] == null) return

            val valueArgument = lambdaExpression.getStrictParentOfType<KtValueArgument>()
            val valueArgumentCalls = valueArgument?.parentCallExpressions().orEmpty()
            if (valueArgumentCalls.any { !it.isApplicableCall(lambdaExpression, lambdaContext) }) return

            val functionLiteralDescriptor = functionLiteral.descriptor
            if (functionLiteralDescriptor != null) {
                if (functionLiteral.anyDescendantOfType<KtNameReferenceExpression> {
                        it.text == "it" && it.resolveToCall()?.resultingDescriptor?.containingDeclaration != functionLiteralDescriptor
                    }) return
            }

            val startOffset = functionLiteral.startOffset
            holder.registerProblem(
                holder.manager.createProblemDescriptor(
                    functionLiteral,
                    TextRange((singleParameter?.startOffset ?: arrow.startOffset) - startOffset, arrow.endOffset - startOffset),
                    KotlinBundle.message("redundant.lambda.arrow"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    DeleteFix()
                )
            )
        })
    }

    class DeleteFix : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("delete.fix.family.name")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtFunctionLiteral ?: return
            element.removeArrow()
        }
    }
}

private fun KtCallExpression.isApplicableCall(lambdaExpression: KtLambdaExpression, lambdaContext: BindingContext): Boolean {
    val qualifiedExpression = parent as? KtQualifiedExpression
    return if (qualifiedExpression == null) {
        val offset = lambdaExpression.textOffset - textOffset
        replaceWithCopyWithResolveCheck(
            resolveStrategy = { expr, context ->
                expr.getResolvedCall(context)?.resultingDescriptor
            },
            context = lambdaContext,
            preHook = {
                findLambdaExpressionByOffset(offset)?.functionLiteral?.removeArrow()
            }
        )
    } else {
        val offset = lambdaExpression.textOffset - qualifiedExpression.textOffset
        qualifiedExpression.replaceWithCopyWithResolveCheck(
            resolveStrategy = { expr, context ->
                expr.selectorExpression.getResolvedCall(context)?.resultingDescriptor
            },
            context = lambdaContext,
            preHook = {
                findLambdaExpressionByOffset(offset)?.functionLiteral?.removeArrow()
            }
        )
    } != null
}

private fun KtFunctionLiteral.removeArrow() {
    valueParameterList?.delete()
    arrow?.delete()
}

private fun KtExpression.findLambdaExpressionByOffset(offset: Int): KtLambdaExpression? {
    val lbrace = findElementAt(offset)?.takeIf { it.node.elementType == KtTokens.LBRACE } ?: return null
    val functionLiteral = lbrace.parent as? KtFunctionLiteral ?: return null
    return functionLiteral.parent as? KtLambdaExpression
}

private fun KtValueArgument.parentCallExpressions(): List<KtCallExpression> {
    val calls = mutableListOf<KtCallExpression>()
    var argument = this
    while (true) {
        val call = argument.getStrictParentOfType<KtCallExpression>() ?: break
        calls.add(call)
        argument = call.getStrictParentOfType() ?: break
    }
    return calls
}
