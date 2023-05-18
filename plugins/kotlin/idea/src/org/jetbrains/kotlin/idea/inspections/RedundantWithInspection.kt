// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.inspections.UnusedLambdaExpressionBodyInspection.Companion.replaceBlockExpressionWithLambdaBody
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class RedundantWithInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        callExpressionVisitor(fun(callExpression) {
            val callee = callExpression.calleeExpression ?: return
            if (callee.text != "with") return

            val valueArguments = callExpression.valueArguments
            if (valueArguments.size != 2) return
            val receiver = valueArguments[0].getArgumentExpression() ?: return
            val lambda = valueArguments[1].lambdaExpression() ?: return
            val lambdaBody = lambda.bodyExpression ?: return

            val context = callExpression.analyze(BodyResolveMode.PARTIAL_WITH_CFA)
            if (lambdaBody.statements.size > 1 && callExpression.isUsedAsExpression(context)) return
            if (callExpression.getResolvedCall(context)?.resultingDescriptor?.fqNameSafe != FqName("kotlin.with")) return

            val lambdaDescriptor = context[BindingContext.FUNCTION, lambda.functionLiteral] ?: return

            var used = false
            lambda.functionLiteral.acceptChildren(object : KtVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    if (used) return
                    element.acceptChildren(this)

                    if (element is KtReturnExpression && element.getLabelName() == "with") {
                        used = true
                        return
                    }

                    if (isUsageOfDescriptor(lambdaDescriptor, element, context)) {
                        used = true
                    }
                }
            })

            if (!used) {
                val quickfix = when (receiver) {
                    is KtSimpleNameExpression, is KtStringTemplateExpression, is KtConstantExpression -> arrayOf(RemoveRedundantWithFix())
                    else -> LocalQuickFix.EMPTY_ARRAY
                }
                holder.registerProblem(
                    callee,
                    KotlinBundle.message("inspection.redundant.with.display.name"),
                    *quickfix
                )
            }
        })
}

private fun KtValueArgument.lambdaExpression(): KtLambdaExpression? =
    (this as? KtLambdaArgument)?.getLambdaExpression() ?: this.getArgumentExpression() as? KtLambdaExpression

private class RemoveRedundantWithFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.redundant.with.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val callExpression = descriptor.psiElement.parent as? KtCallExpression ?: return
        val lambdaExpression = callExpression.valueArguments.getOrNull(1)?.lambdaExpression() ?: return
        val lambdaBody = lambdaExpression.bodyExpression ?: return

        val function = callExpression.getStrictParentOfType<KtFunction>()
        val functionBody = KtPsiUtil.deparenthesize(function?.bodyExpression)

        val replaced = if (function?.equalsToken != null && functionBody == callExpression) {
            val singleStatement = lambdaBody.statements.singleOrNull()?.let {
                (it as? KtReturnExpression)?.returnedExpression ?: it
            }
            if (singleStatement != null) {
                callExpression.replaced(singleStatement)
            } else {
                function.replaceBlockExpressionWithLambdaBody(lambdaBody)
                function.bodyExpression
            }
        } else {
            val result = lambdaBody.allChildren.takeUnless { it.isEmpty }?.let { range ->
                callExpression.parent.addRangeAfter(range.first, range.last, callExpression)
            }
            callExpression.delete()
            result
        }

        replaced?.findExistingEditor()?.moveCaret(replaced.startOffset)
    }
}
