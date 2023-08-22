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
import org.jetbrains.kotlin.idea.intentions.isArrayOfFunction
import org.jetbrains.kotlin.idea.refactoring.replaceWithCopyWithResolveCheck
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class RemoveRedundantSpreadOperatorInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return valueArgumentVisitor(fun(argument) {
            val spreadElement = argument.getSpreadElement() ?: return
            if (argument.isNamed()) return
            val argumentExpression = argument.getArgumentExpression() ?: return
            val argumentOffset = argument.startOffset
            val startOffset = spreadElement.startOffset - argumentOffset
            val endOffset =
                when (argumentExpression) {
                    is KtCallExpression -> {
                        if (!argumentExpression.isArrayOfFunction()) return
                        val call = argument.getStrictParentOfType<KtCallExpression>() ?: return
                        val bindingContext = call.analyze(BodyResolveMode.PARTIAL)
                        val argumentIndex = call.valueArguments.indexOfFirst { it == argument }
                        if (call.replaceWithCopyWithResolveCheck(
                                resolveStrategy = { expr, context -> expr.getResolvedCall(context)?.resultingDescriptor },
                                context = bindingContext,
                                preHook = {
                                    val anchor = valueArgumentList?.arguments?.getOrNull(argumentIndex)
                                    argumentExpression.valueArguments.reversed().forEach {
                                        valueArgumentList?.addArgumentAfter(it, anchor)
                                    }
                                    valueArgumentList?.removeArgument(argumentIndex)
                                }
                            ) == null
                        ) return

                        argumentExpression.calleeExpression!!.endOffset - argumentOffset
                    }
                    is KtCollectionLiteralExpression -> startOffset + 1
                    else -> return
                }

            val problemDescriptor = holder.manager.createProblemDescriptor(
                argument,
                TextRange(startOffset, endOffset),
                KotlinBundle.message("remove.redundant.spread.operator.quickfix.text"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                RemoveRedundantSpreadOperatorQuickfix()
            )
            holder.registerProblem(problemDescriptor)
        })
    }
}

class RemoveRedundantSpreadOperatorQuickfix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.redundant.spread.operator.quickfix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // Argument & expression under *
        val spreadValueArgument = descriptor.psiElement as? KtValueArgument ?: return
        val spreadArgumentExpression = spreadValueArgument.getArgumentExpression() ?: return
        val outerArgumentList = spreadValueArgument.getStrictParentOfType<KtValueArgumentList>() ?: return
        // Arguments under arrayOf or []
        val innerArgumentExpressions =
            when (spreadArgumentExpression) {
                is KtCallExpression -> spreadArgumentExpression.valueArgumentList?.arguments?.map {
                    it.getArgumentExpression() to it.isSpread
                }
                is KtCollectionLiteralExpression -> spreadArgumentExpression.getInnerExpressions().map { it to false }
                else -> null
            } ?: return

        val factory = KtPsiFactory(project)
        innerArgumentExpressions.reversed().forEach { (expression, isSpread) ->
            outerArgumentList.addArgumentAfter(factory.createArgument(expression, isSpread = isSpread), spreadValueArgument)
        }
        outerArgumentList.removeArgument(spreadValueArgument)
    }
}