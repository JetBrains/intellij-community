// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.IfThenToSafeAccessFix
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationStrategy
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationUtils
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*

internal class IfThenToSafeAccessInspection :
    KotlinApplicableInspectionBase.Simple<KtIfExpression, IfThenTransformationStrategy>(),
    CleanupLocalInspectionTool {

    override fun getProblemDescription(element: KtIfExpression, context: IfThenTransformationStrategy): @InspectionMessage String =
        KotlinBundle.message("foldable.if.then")

    override fun getProblemHighlightType(element: KtIfExpression, context: IfThenTransformationStrategy): ProblemHighlightType {
        if (!context.shouldSuggestTransformation()) return ProblemHighlightType.INFORMATION

        return super.getProblemHighlightType(element, context)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> = ApplicabilityRanges.ifExpressionExcludingBranches(element)

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        val data = IfThenTransformationUtils.buildTransformationData(element) ?: return false

        // negated clause is present, but it does not evaluate to `null`
        if (data.negatedClause?.isNullExpression() == false) return false

        val condition = data.condition
        return condition !is KtIsExpression || condition.typeReference != null
    }

    override fun KaSession.prepareContext(element: KtIfExpression): IfThenTransformationStrategy? {
        val data = IfThenTransformationUtils.buildTransformationData(element) ?: return null
        // there are no usages of expression, except possibly at nested levels, which are currently not supported
        if (data.checkedExpression !is KtThisExpression && IfThenTransformationUtils.collectCheckedExpressionUsages(data).isEmpty()) return null
        return IfThenTransformationUtils.prepareIfThenTransformationStrategy(element, false)
    }

    override fun createQuickFix(
        element: KtIfExpression,
        context: IfThenTransformationStrategy,
    ): KotlinModCommandQuickFix<KtIfExpression> = IfThenToSafeAccessFix(context)
}