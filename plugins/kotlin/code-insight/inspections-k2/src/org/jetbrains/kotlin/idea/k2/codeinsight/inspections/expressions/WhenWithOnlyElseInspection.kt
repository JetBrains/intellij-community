// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.replaceWithBranch
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * This inspection finds and replaces `when` expressions containing only a single `else` branch by the body of that branch, accounting for
 * the presence of a subject variable. In general, it rewrites:
 *
 * ```kotlin
 *   when (val x = e1) {
 *     else -> e2
 *   }
 * ```
 *
 * into:
 *
 * ```
 *   run {
 *     val x = e1
 *     e2
 *   }
 * ```
 */
internal class WhenWithOnlyElseInspection
    : KotlinApplicableInspectionBase.Simple<KtWhenExpression, WhenWithOnlyElseInspection.Context>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitWhenExpression(expression: KtWhenExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    data class Context(
        val isWhenUsedAsExpression: Boolean,
        val elseExpression: SmartPsiElementPointer<KtExpression>,
    )

    override fun getProblemDescription(element: KtWhenExpression, context: Context): String =
        KotlinBundle.message("when.has.only.else.branch.and.should.be.simplified")

    override fun getApplicableRanges(element: KtWhenExpression): List<TextRange> =
        ApplicabilityRanges.whenKeyword(element)

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = element.entries.singleOrNull()?.isElse == true

    override fun KaSession.prepareContext(element: KtWhenExpression): Context? {
        val singleEntry = element.entries.singleOrNull() ?: return null
        val elseExpression = singleEntry.takeIf { it.isElse }?.expression ?: return null
        val isWhenUsedAsExpression = element.isUsedAsExpression

        return Context(isWhenUsedAsExpression, elseExpression.createSmartPointer())
    }

    override fun createQuickFix(
        element: KtWhenExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtWhenExpression> = object : KotlinModCommandQuickFix<KtWhenExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.when.with.only.else.action.name")

        override fun applyFix(
            project: Project,
            element: KtWhenExpression,
            updater: ModPsiUpdater,
        ) {
            val newCaretPosition = element.startOffset
            val elseExpression: KtExpression = context.elseExpression.dereference()?.let(updater::getWritable) ?: return

            element.replaceWithBranch(elseExpression, context.isWhenUsedAsExpression)

            updater.moveCaretTo(newCaretPosition)
        }
    }
}
