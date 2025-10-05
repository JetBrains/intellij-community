// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.inspections.UnusedLambdaExpressionBodyInspection.Util.replaceBlockExpressionWithLambdaBody
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnit

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
            if (!callExpression.isCalling(FqName("kotlin.with"), context)) return

            if (lambdaBody.statements.size > 1 &&
                callExpression.isUsedAsExpression(context) &&
                callExpression.getStrictParentOfType<KtFunction>()?.bodyExpression?.deparenthesize() != callExpression
            ) return

            val functionLiteral = lambda.functionLiteral
            val lambdaDescriptor = context[BindingContext.FUNCTION, functionLiteral] ?: return
            val used = functionLiteral.anyDescendantOfType<KtElement> {
                (it as? KtReturnExpression)?.getLabelName() == "with" || isUsageOfDescriptor(lambdaDescriptor, it, context)
            }

            if (!used) {
                val fixes = when (receiver) {
                    is KtSimpleNameExpression, is KtStringTemplateExpression, is KtConstantExpression -> arrayOf(RemoveRedundantWithFix())
                    else -> LocalQuickFix.EMPTY_ARRAY
                }
                holder.registerProblem(callee, KotlinBundle.message("inspection.redundant.with.display.name"), *fixes)
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
        val functionBody = function?.bodyExpression

        val replaced = if (functionBody?.deparenthesize() == callExpression) {
            val singleStatement = lambdaBody.statements.singleOrNull()
            if (singleStatement != null) {
                callExpression.replaced(
                    (singleStatement as? KtReturnExpression)?.returnedExpression ?: singleStatement
                )
            } else {
                val returnType = (function.descriptor as? FunctionDescriptor)?.returnType
                if (returnType != null && !returnType.isUnit()) {
                    function.setType(returnType, shortenReferences = true)
                }
                val lastStatement = lambdaBody.statements.lastOrNull()
                if (lastStatement != null && lastStatement !is KtReturnExpression) {
                    lastStatement.replaced(
                        KtPsiFactory(project).createExpressionByPattern("return $0", lastStatement)
                    )
                }
                function.replaceBlockExpressionWithLambdaBody(lambdaBody)
                functionBody
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
