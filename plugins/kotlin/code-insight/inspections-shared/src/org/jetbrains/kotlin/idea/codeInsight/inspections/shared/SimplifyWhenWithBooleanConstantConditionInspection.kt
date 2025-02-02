// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SimplifyWhenWithBooleanConstantConditionInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.replaceWithBranch
import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*

/**
 * Tests:
 *  - [org.jetbrains.kotlin.idea.codeInsight.inspections.shared.SharedK1LocalInspectionTestGenerated.SimplifyWhenWithBooleanConstantCondition]
 *  - [org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared.SharedK2LocalInspectionTestGenerated.SimplifyWhenWithBooleanConstantCondition]
 */
internal class SimplifyWhenWithBooleanConstantConditionInspection : KotlinApplicableInspectionBase.Simple<KtWhenExpression, Context>() {
    data class Context(val isUsedAsExpression: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitWhenExpression(expression: KtWhenExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(element: KtWhenExpression, context: Context): @InspectionMessage String =
        KotlinBundle.message("this.when.is.simplifiable")

    override fun getApplicableRanges(element: KtWhenExpression): List<TextRange> =
        ApplicabilityRanges.whenKeyword(element)

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean {
        if (element.closeBrace == null) return false
        if (element.subjectExpression != null) return false
        if (element.entries.none { it.isTrueConstantCondition() || it.isFalseConstantCondition() }) return false
        return true
    }

    override fun KaSession.prepareContext(element: KtWhenExpression): Context {
        return Context(element.isUsedAsExpression)
    }

    override fun createQuickFixes(element: KtWhenExpression, context: Context): Array<KotlinModCommandQuickFix<KtWhenExpression>> =
        arrayOf(SimplifyFix(context))
}

private class SimplifyFix(private val context: Context) : KotlinModCommandQuickFix<KtWhenExpression>() {
    override fun getFamilyName(): String = KotlinBundle.message("simplify.when.fix.text")

    override fun applyFix(project: Project, element: KtWhenExpression, updater: ModPsiUpdater) {
        val factory = KtPsiFactory(project)
        val closeBrace = element.closeBrace ?: return
        element.deleteFalseEntries(context.isUsedAsExpression, updater)
        element.replaceTrueEntry(context.isUsedAsExpression, closeBrace, factory, updater)
    }
}

private fun KtWhenExpression.deleteFalseEntries(isUsedAsExpression: Boolean, updater: ModPsiUpdater) {
    for (entry in entries) {
        if (entry.isFalseConstantCondition()) {
            entry.delete()
        }
    }

    val entries = entries
    if (entries.isEmpty() && !isUsedAsExpression) {
        delete()
    } else if (entries.singleOrNull()?.isElse == true) {
        elseExpression?.let {
            val replacedBranch = replaceWithBranch(it, isUsedAsExpression)
            if (replacedBranch != null) {
                updater.moveCaretTo(replacedBranch.startOffset)
            }
        }
    }
}

private fun KtWhenExpression.replaceTrueEntry(
    isUsedAsExpression: Boolean,
    closeBrace: PsiElement,
    factory: KtPsiFactory,
    updater: ModPsiUpdater
) {
    val entries = entries
    val trueIndex = entries.indexOfFirst { it.isTrueConstantCondition() }
    if (trueIndex == -1) return

    val expression = entries[trueIndex].expression ?: return

    if (trueIndex == 0) {
        val replacedBranch = replaceWithBranch(expression, isUsedAsExpression)
        if (replacedBranch != null) {
            updater.moveCaretTo(replacedBranch.startOffset)
        }
    } else {
        val elseEntry = factory.createWhenEntry("else -> ${expression.text}")
        for (entry in entries.subList(trueIndex, entries.size)) {
            entry.delete()
        }
        addBefore(elseEntry, closeBrace)
    }
}

private fun KtWhenEntry.isTrueConstantCondition(): Boolean =
    (conditions.singleOrNull() as? KtWhenConditionWithExpression)?.expression.isTrueConstant()

private fun KtWhenEntry.isFalseConstantCondition(): Boolean =
    (conditions.singleOrNull() as? KtWhenConditionWithExpression)?.expression.isFalseConstant()