// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.samConstructorCallsToBeConverted
import org.jetbrains.kotlin.idea.base.psi.replaceSamConstructorCall
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

/**
 * Tests:
 *  * [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.RedundantSamConstructor]
 *  * [org.jetbrains.kotlin.idea.inspections.InspectionTestGenerated.Inspections.testRedundantSamConstructor_inspectionData_Inspections_test]
 */
class RedundantSamConstructorInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return callExpressionVisitor(fun(expression) {
            if (expression.valueArguments.isEmpty()) return

            val samConstructorCalls = analyze(expression) {
                samConstructorCallsToBeConverted(expression)
            }
            if (samConstructorCalls.isEmpty()) return
            val single = samConstructorCalls.singleOrNull()
            if (single != null) {
                val calleeExpression = single.calleeExpression ?: return
                val problemDescriptor = holder.manager.createProblemDescriptor(
                    single.getQualifiedExpressionForSelector()?.receiverExpression ?: calleeExpression,
                    single.typeArgumentList ?: calleeExpression,
                    KotlinBundle.message("redundant.sam.constructor"),
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    isOnTheFly,
                    createQuickFix(single)
                )

                holder.registerProblem(problemDescriptor)
            } else {
                val problemDescriptor = holder.manager.createProblemDescriptor(
                    expression.valueArgumentList!!,
                    KotlinBundle.message("redundant.sam.constructors"),
                    createQuickFix(samConstructorCalls),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly
                )

                holder.registerProblem(problemDescriptor)
            }
        })
    }

    private fun createQuickFix(expression: KtCallExpression): PsiUpdateModCommandQuickFix {
        val pointer = expression.createSmartPointer()
        return object : PsiUpdateModCommandQuickFix() {
            override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.sam.constructor")
            override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
                val callExpression = updater.getWritable(pointer.element) ?: return
                removeSamConstructor(callExpression)
            }
        }
    }

    private fun createQuickFix(expressions: Collection<KtCallExpression>): PsiUpdateModCommandQuickFix {
        val pointers = expressions.map { it.createSmartPointer() }
        return object : PsiUpdateModCommandQuickFix() {
            override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.sam.constructors")
            override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
                val callExpressions = pointers.mapNotNull { updater.getWritable(it.element) }
                for (callExpression in callExpressions) {
                    removeSamConstructor(callExpression)
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    private fun removeSamConstructor(callExpression: KtCallExpression) {
        val lambdaExpression = replaceSamConstructorCall(callExpression)
        val outerCallExpression = lambdaExpression.parentOfType<KtCallExpression>() ?: return
        val canMoveLambdaOutsideParentheses = allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(outerCallExpression) {
                    outerCallExpression.canMoveLambdaOutsideParentheses(skipComplexCalls = true)
                }
            }
        }
        if (canMoveLambdaOutsideParentheses) {
            outerCallExpression.moveFunctionLiteralOutsideParentheses()
        }
    }

}
