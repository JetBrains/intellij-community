// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.AddBracesUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertIfToWhen
import org.jetbrains.kotlin.psi.*

internal class SuspiciousCascadingIfInspection : KotlinApplicableInspectionBase<KtIfExpression, Unit>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        ifExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        if (element.condition == null || element.then == null) return false
        if (element.parent.node.elementType == KtNodeTypes.ELSE) return false

        val candidateExpression = when (val lastElseBranch = element.findLastElseBranch()) {
            is KtDotQualifiedExpression -> lastElseBranch.receiverExpression
            is KtBinaryExpression -> lastElseBranch.left
            else -> null
        }

        return candidateExpression is KtIfExpression
    }

    override fun KaSession.prepareContext(element: KtIfExpression) {}

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> =
        ApplicabilityRanges.ifKeyword(element)

    override fun InspectionManager.createProblemDescriptor(
        element: KtIfExpression,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("inspection.suspicious.cascading.if.display.name"),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ ConvertIfToWhenFix(), AddBracesToElseFix(),
        )
    }
}

private class AddBracesToElseFix() : KotlinModCommandQuickFix<KtIfExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.clarifying.braces.to.nested.else.statement")

    override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
        val lastElseBranch = element.findLastElseBranch() ?: return
        val parent = lastElseBranch.parent as? KtElement ?: return
        AddBracesUtils.addBraces(parent, lastElseBranch)
        updater.moveCaretTo(element.startOffset)
    }
}

private class ConvertIfToWhenFix() : KotlinModCommandQuickFix<KtIfExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.if.with.when.changes.semantics")

    override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(project)
        when (val lastElseBranch = element.findLastElseBranch()) {
            is KtBinaryExpression -> {
                val operationText = lastElseBranch.operationReference.text
                val rightText = lastElseBranch.right?.text ?: ""
                val nestedIf = lastElseBranch.left ?: return
                lastElseBranch.replace(nestedIf)
                val whenExpr = convertIfToWhen(element, updater)
                val outerBinaryExpr = psiFactory.createExpressionByPattern("$0 $1 $2", whenExpr, operationText, rightText)
                whenExpr.replace(outerBinaryExpr)
            }

            is KtDotQualifiedExpression -> {
                val selectorText = lastElseBranch.selectorExpression?.text ?: ""
                val nestedIf = lastElseBranch.receiverExpression
                lastElseBranch.replace(nestedIf)
                val whenExpr = convertIfToWhen(element, updater)
                val outerQualifiedExpr = psiFactory.createExpressionByPattern("$0.$1", whenExpr, selectorText)
                whenExpr.replace(outerQualifiedExpr)
            }
        }
    }
}

private fun KtIfExpression.findLastElseBranch(): KtExpression? {
    var lastElseBranch = this.`else`
    while (lastElseBranch is KtIfExpression) {
        lastElseBranch = lastElseBranch.`else`
    }
    return lastElseBranch
}
