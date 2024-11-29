// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.IfThenToElviFix
import org.jetbrains.kotlin.idea.codeInsight.IfThenToElvisInspectionData
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationUtils
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.ifExpressionVisitor

internal class IfThenToElvisInspection @JvmOverloads constructor(
    @JvmField var highlightStatement: Boolean = false
) : KotlinApplicableInspectionBase.Simple<KtIfExpression, IfThenToElvisInspectionData>(), CleanupLocalInspectionTool {

    override fun getProblemDescription(element: KtIfExpression, context: IfThenToElvisInspectionData): String =
        KotlinBundle.message("if.then.foldable.to")

    override fun createQuickFix(
        element: KtIfExpression,
        context: IfThenToElvisInspectionData
    ): KotlinModCommandQuickFix<KtIfExpression> = IfThenToElviFix(context)

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> =
        ApplicabilityRanges.ifExpressionExcludingBranches(element)

    override fun getProblemHighlightType(element: KtIfExpression, context: IfThenToElvisInspectionData): ProblemHighlightType {
        return if (context.transformationStrategy.shouldSuggestTransformation() && (highlightStatement || context.isUsedAsExpression)) {
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        } else {
            ProblemHighlightType.INFORMATION
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = ifExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    context(KaSession)
    override fun prepareContext(element: KtIfExpression): IfThenToElvisInspectionData? =
        IfThenTransformationUtils.prepareIfThenToElvisInspectionData(element)

    override fun getOptionsPane() = pane(
        checkbox("highlightStatement", KotlinBundle.message("report.also.on.statement"))
    )
}