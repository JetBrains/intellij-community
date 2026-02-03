// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.removeUnnecessaryParentheses
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

class RemoveUnnecessaryParenthesesInspection : KotlinApplicableInspectionBase.Simple<KtParenthesizedExpression, Unit>() {
    override fun getProblemDescription(
        element: KtParenthesizedExpression, context: Unit
    ): @InspectionMessage String = KotlinBundle.message("inspection.remove.unnecessary.parentheses.problem.description")

    override fun createQuickFix(
        element: KtParenthesizedExpression, context: Unit
    ): KotlinModCommandQuickFix<KtParenthesizedExpression> = object : KotlinModCommandQuickFix<KtParenthesizedExpression>() {
        override fun getFamilyName() = KotlinBundle.message("inspection.remove.unnecessary.parentheses.quickfix.text")

        override fun applyFix(project: Project, element: KtParenthesizedExpression, updater: ModPsiUpdater) {
            element.removeUnnecessaryParentheses()
        }
    }

    override fun getApplicableRanges(element: KtParenthesizedExpression): List<TextRange> {
        val inner = element.expression ?: return emptyList()

        if (!KtPsiUtil.areParenthesesUseless(element)) return emptyList()

        val elementRange = element.textRange
        val innerRange = inner.textRange
        val left = if (innerRange.startOffset > elementRange.startOffset)
            TextRange(0, innerRange.startOffset - elementRange.startOffset) else null
        val right = if (innerRange.endOffset < elementRange.endOffset)
            TextRange(innerRange.endOffset - elementRange.startOffset, elementRange.length) else null
        return listOfNotNull(left, right)
    }

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitParenthesizedExpression(expression: KtParenthesizedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtParenthesizedExpression) {}
}