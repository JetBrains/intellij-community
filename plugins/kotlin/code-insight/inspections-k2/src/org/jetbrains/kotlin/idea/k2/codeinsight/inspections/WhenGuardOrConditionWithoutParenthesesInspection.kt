// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.*

internal class WhenGuardOrConditionWithoutParenthesesInspection : KotlinApplicableInspectionBase.Simple<KtWhenEntryGuard, Unit>() {
    override fun getProblemDescription(
        element: KtWhenEntryGuard,
        context: Unit,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.or.without.parentheses.in.when.guard.problem.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> {
        return object : KtVisitorVoid() {
            override fun visitWhenEntry(ktWhenEntry: KtWhenEntry) {
                ktWhenEntry.guard?.let { visitTargetElement(it, holder, isOnTheFly) }
            }
        }
    }

    override fun isApplicableByPsi(element: KtWhenEntryGuard): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.WhenGuards)) return false
        val guardExpression = element.getExpression()
        return guardExpression is KtBinaryExpression && guardExpression.operationToken == org.jetbrains.kotlin.lexer.KtTokens.OROR
    }

    override fun getApplicableRanges(element: KtWhenEntryGuard): List<TextRange> {
        return ApplicabilityRange.single(element, KtWhenEntryGuard::getExpression)
    }

    override fun createQuickFix(
        element: KtWhenEntryGuard,
        context: Unit,
    ): KotlinModCommandQuickFix<KtWhenEntryGuard> {
        return object : KotlinModCommandQuickFix<KtWhenEntryGuard>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("inspection.or.without.parentheses.in.when.guard.fix.text")

            override fun applyFix(
                project: Project,
                element: KtWhenEntryGuard,
                updater: ModPsiUpdater,
            ) {
                val expression = element.getExpression() as? KtBinaryExpression ?: return
                val parenthesized = KtPsiFactory(project).createExpression("(${expression.text})")
                expression.replace(parenthesized)
            }
        }
    }

    override fun KaSession.prepareContext(element: KtWhenEntryGuard): Unit? = Unit
}
