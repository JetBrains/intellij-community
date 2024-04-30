// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationUtils
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationData
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationStrategy
import org.jetbrains.kotlin.idea.codeInsight.TransformIfThenReceiverMode
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
        if (condition is KtIsExpression && condition.typeReference == null) return false

        // there are no usages of expression, except possibly at nested levels, which are currently not supported
        if (data.checkedExpression !is KtThisExpression && IfThenTransformationUtils.collectTextBasedUsages(data).isEmpty()) return false

        return true
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtIfExpression): IfThenTransformationStrategy? {
        val data = IfThenTransformationUtils.buildTransformationData(element) as IfThenTransformationData

        if (data.negatedClause == null && data.baseClause.isUsedAsExpression()) return null

        // every usage is expected to have smart cast info;
        // if smart cast is unstable, replacing usage with `it` can break code logic
        if (IfThenTransformationUtils.collectTextBasedUsages(data).any { it.doesNotHaveStableSmartCast() }) return null

        if (conditionIsSenseless(data)) return null

        return IfThenTransformationStrategy.create(data)
    }

    override fun createQuickFix(
        element: KtIfExpression,
        context: IfThenTransformationStrategy,
    ): KotlinModCommandQuickFix<KtIfExpression> = object : KotlinModCommandQuickFix<KtIfExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("simplify.foldable.if.then")

        override fun getName(): @IntentionName String {
            val transformReceiverMode = (context as? IfThenTransformationStrategy.AddSafeAccess)?.transformReceiverMode

            return if (transformReceiverMode == TransformIfThenReceiverMode.REPLACE_BASE_CLAUSE) {
                if (context.newReceiverIsSafeCast) {
                    KotlinBundle.message("replace.if.expression.with.safe.cast.expression")
                } else {
                    KotlinBundle.message("remove.redundant.if.expression")
                }
            } else {
                KotlinBundle.message("replace.if.expression.with.safe.access.expression")
            }
        }

        override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
            val data = IfThenTransformationUtils.buildTransformationData(element) as IfThenTransformationData
            val transformedBaseClause = IfThenTransformationUtils.transformBaseClause(data, context.withWritableData(updater))

            element.replace(transformedBaseClause)
        }
    }

    context(KtAnalysisSession)
    private fun KtExpression.doesNotHaveStableSmartCast(): Boolean {
        val expressionToCheck = when (this) {
            is KtThisExpression -> instanceReference
            else -> this
        }
        return expressionToCheck.getSmartCastInfo()?.isStable != true
    }


    context(KtAnalysisSession)
    private fun conditionIsSenseless(data: IfThenTransformationData): Boolean = data.condition
        .getDiagnostics(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        .map { it.diagnosticClass }
        .any { it == KtFirDiagnostic.SenselessComparison::class || it == KtFirDiagnostic.UselessIsCheck::class }
}