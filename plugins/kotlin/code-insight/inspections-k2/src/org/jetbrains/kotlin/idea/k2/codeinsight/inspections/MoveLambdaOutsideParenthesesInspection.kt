// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.refactoring.isComplexCallWithLambdaArgument
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class MoveLambdaOutsideParenthesesInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit,
    ): String = KotlinBundle.message(
        "lambda.argument.0.be.moved.out",
        if (element.isComplexCallWithLambdaArgument()) 0 else 1,
    )

    override fun getProblemHighlightType(
        element: KtCallExpression,
        context: Unit,
    ): ProblemHighlightType =
        if (element.isComplexCallWithLambdaArgument()) ProblemHighlightType.INFORMATION
        else super.getProblemHighlightType(element, context)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        return if (!element.canMoveLambdaOutsideParentheses(skipComplexCalls = false)) null else Unit
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("move.lambda.argument.out.of.parentheses")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            element.moveFunctionLiteralOutsideParentheses(updater::moveCaretTo)
        }
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
        val textRange = element.getLastLambdaExpression()?.functionLiteral?.lBrace?.textRangeIn(element)
        return listOfNotNull(textRange)
    }
}