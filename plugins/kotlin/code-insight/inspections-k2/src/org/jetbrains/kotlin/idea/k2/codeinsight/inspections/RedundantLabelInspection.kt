// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.*

internal class RedundantLabelInspection : KotlinApplicableInspectionBase.Simple<KtLabeledExpression, Unit>() {
    override fun getProblemDescription(
        element: KtLabeledExpression,
        context: Unit,
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.redundant.label.problem.description")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitLabeledExpression(expression: KtLabeledExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }

    override fun isApplicableByPsi(element: KtLabeledExpression): Boolean {
        val labelNameExpression = element.getTargetLabel()

        if (labelNameExpression != null) {
            val deparenthesizedBaseExpression = KtPsiUtil.deparenthesize(element)
            if (deparenthesizedBaseExpression !is KtLambdaExpression &&
                deparenthesizedBaseExpression !is KtLoopExpression &&
                deparenthesizedBaseExpression !is KtNamedFunction
            ) {
                return true
            }
        }

        return false
    }

    override fun getApplicableRanges(element: KtLabeledExpression): List<TextRange> {
        return ApplicabilityRange.single(element) {
            element.getTargetLabel() ?: element
        }
    }

    override fun createQuickFix(
        element: KtLabeledExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtLabeledExpression> = object : KotlinModCommandQuickFix<KtLabeledExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String {
            return KotlinBundle.message("remove.redundant.label")
        }

        override fun applyFix(
            project: Project,
            element: KtLabeledExpression,
            updater: ModPsiUpdater
        ) {
            val baseExpression = element.baseExpression ?: return
            element.replace(baseExpression)
        }

    }

    override fun KaSession.prepareContext(element: KtLabeledExpression) = Unit
}