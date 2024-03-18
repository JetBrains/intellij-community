// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.k2.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.refactoring.isComplexCallWithLambdaArgument
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class MoveLambdaOutsideParenthesesInspection : AbstractKotlinApplicableInspection<KtCallExpression>() {
    override fun getProblemDescription(element: KtCallExpression): @InspectionMessage String {
        return KotlinBundle.message("lambda.argument.0.be.moved.out",
                             if (element.isComplexCallWithLambdaArgument()) 0 else 1)
    }

    override fun getProblemHighlightType(element: KtCallExpression): ProblemHighlightType {
        return if (element.isComplexCallWithLambdaArgument()) ProblemHighlightType.INFORMATION else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean = element.canMoveLambdaOutsideParentheses(skipComplexCalls = false)

    override fun apply(element: KtCallExpression, project: Project, updater: ModPsiUpdater) {
        element.moveFunctionLiteralOutsideParentheses(updater::moveCaretTo)
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallExpression> {
        return applicabilityRange { element: KtCallExpression ->
            element.getLastLambdaExpression()
                ?.getStrictParentOfType<KtValueArgument>()?.asElement()
                ?.textRangeIn(element)
        }
    }

    override fun getActionFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("move.lambda.argument.out.of.parentheses")
    }
}