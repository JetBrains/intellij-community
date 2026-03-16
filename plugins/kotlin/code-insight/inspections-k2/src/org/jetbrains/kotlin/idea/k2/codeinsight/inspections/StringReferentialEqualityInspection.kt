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
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.StringReferentialEqualityInspection.Context
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal class StringReferentialEqualityInspection : KotlinApplicableInspectionBase<KtBinaryExpression, Context>() {
    data class Context(val isEquals: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        val left = element.left ?: return false
        val right = element.right ?: return false
        val op = element.operationToken
        return (op == KtTokens.EQEQEQ || op == KtTokens.EXCLEQEQEQ) &&
                left.node?.elementType != KtNodeTypes.NULL &&
                right.node?.elementType != KtNodeTypes.NULL
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        val left = element.left ?: return null
        val right = element.right ?: return null

        if (left.expressionType?.isStringType != true || right.expressionType?.isStringType != true) {
            return null
        }

        val isEquals = element.operationToken == KtTokens.EQEQEQ
        return Context(isEquals)
    }

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.operationReference }

    override fun InspectionManager.createProblemDescriptor(
        element: KtBinaryExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val messageKey = if (context.isEquals) {
            "string.comparison.using.referential.equality.triple.equals"
        } else {
            "string.comparison.using.referential.equality.triple.not.equals"
        }
        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message(messageKey),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ ReplaceWithStructuralEqualityFix(context.isEquals)
        )
    }
}

private class ReplaceWithStructuralEqualityFix(private val isEquals: Boolean) : KotlinModCommandQuickFix<KtBinaryExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        if (isEquals) {
            KotlinBundle.message("replace.triple.equals.with.double.equals")
        } else {
            KotlinBundle.message("replace.triple.not.equals.with.double.not.equals")
        }

    override fun applyFix(project: Project, element: KtBinaryExpression, updater: ModPsiUpdater) {
        val left = element.left ?: return
        val right = element.right ?: return
        val operator = if (isEquals) "==" else "!="
        val newExpression = KtPsiFactory(project).createExpressionByPattern("$0 $operator $1", left, right)
        element.replace(newExpression)
    }
}
