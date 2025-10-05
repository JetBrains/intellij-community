// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.canUseRangeUntil
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

/**
 * Inspection that suggests replacing the `until` operator with the `rangeUntil` operator (`..<`).
 */
class ReplaceUntilWithRangeUntilInspection : AbstractRangeInspection<Unit>() {
    override fun getProblemDescription(
        range: RangeExpression,
        context: Unit
    ): @InspectionMessage String = KotlinBundle.message("until.can.be.replaced.with.rangeUntil.operator")

    override fun isApplicableByPsi(range: RangeExpression): Boolean {
        return range.type == RangeKtExpressionType.UNTIL
    }

    override fun KaSession.prepareContext(range: RangeExpression): Unit? {
        return range.expression.canUseRangeUntil().asUnit
    }

    override fun createQuickFix(
        range: RangeExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtExpression> = object : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.0.operator", "..<")

        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val (left, right) = range.arguments
            if (left == null || right == null) return

            //KotlinLanguageFeaturesFUSCollector.rangeUntilCollector.logQuickFixApplied(element.containingFile) TODO uncomment it after KTIJ-34936
            element.replace(
                KtPsiFactory(project).createExpressionByPattern("$0..<$1", left, right)
            )
        }
    }
}
