// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractRangeInspection.Companion.rangeExpressionByPsi
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.canUseRangeUntil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.DeprecationCollectingInspection
import org.jetbrains.kotlin.idea.statistics.KotlinLanguageFeaturesFUSCollector
import org.jetbrains.kotlin.idea.statistics.NewAndDeprecatedFeaturesInspectionData
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

class ReplaceUntilWithRangeUntilInspection : DeprecationCollectingInspection<NewAndDeprecatedFeaturesInspectionData>(
    collector = KotlinLanguageFeaturesFUSCollector.rangeUntilCollector,
    defaultDeprecationData = NewAndDeprecatedFeaturesInspectionData()
) {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitBinaryExpression(binaryExpression: KtBinaryExpression) {
                visitRange(binaryExpression, holder, session)
            }

            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                visitRange(expression, holder, session)
            }
        }

    private fun visitRange(expression: KtExpression, holder: ProblemsHolder, session: LocalInspectionToolSession) {
        val rangeExpression = rangeExpressionByPsi(expression) ?: return

        analyze(expression) {
            val call = expression.resolveToCall()?.singleFunctionCallOrNull()
            val packageName = call?.symbol?.callableId?.packageName
            if (packageName == null || !packageName.startsWith(Name.identifier("kotlin"))) return
            if (!rangeExpression.expression.canUseRangeUntil()) return
        }

        val rangeKtExpressionType = rangeExpression.type
        session.updateDeprecationData {
            when (rangeKtExpressionType) {
                RangeKtExpressionType.UNTIL -> it.withDeprecatedFeature()
                RangeKtExpressionType.RANGE_UNTIL -> it.withNewFeature()
                else -> it
            }
        }
        if (rangeKtExpressionType != RangeKtExpressionType.UNTIL) return

        holder.registerProblem(
            expression,
            KotlinBundle.message("until.can.be.replaced.with.rangeUntil.operator"),
            ReplaceFix()
        )
    }

    private class ReplaceFix : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("replace.with.0.operator", "..<")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtExpression ?: return
            val rangeExpression = rangeExpressionByPsi(expression) ?: return
            val (left, right) = rangeExpression.arguments
            if (left == null || right == null) return
            KotlinLanguageFeaturesFUSCollector.rangeUntilCollector.logQuickFixApplied(expression.containingFile)
            expression.replace(KtPsiFactory(project).createExpressionByPattern("$0..<$1", left, right))
        }
    }
}
