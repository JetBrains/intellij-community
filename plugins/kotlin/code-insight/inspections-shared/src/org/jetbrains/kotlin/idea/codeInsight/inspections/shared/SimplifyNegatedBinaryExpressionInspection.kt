// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.canBeSimplified
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.canBeSimplifiedWithoutChangingSemantics
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.negate
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils.simplify
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.prefixExpressionVisitor


internal class SimplifyNegatedBinaryExpressionInspection :
    KotlinApplicableInspectionBase.Simple<KtPrefixExpression, Boolean>() {

    override fun KaSession.prepareContext(element: KtPrefixExpression): Boolean? =
        if (element.canBeSimplified()) element.canBeSimplifiedWithoutChangingSemantics() else null

    override fun isApplicableByPsi(element: KtPrefixExpression): Boolean = element.canBeSimplified()

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = prefixExpressionVisitor{
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(element: KtPrefixExpression, context: Boolean): String =
        KotlinBundle.message("negated.operation.can.be.simplified")

    override fun getProblemHighlightType(element: KtPrefixExpression, context: Boolean): ProblemHighlightType =
        if (context) GENERIC_ERROR_OR_WARNING else INFORMATION

    override fun createQuickFix(
        element: KtPrefixExpression,
        context: Boolean,
    ): KotlinModCommandQuickFix<KtPrefixExpression> = object : KotlinModCommandQuickFix<KtPrefixExpression>() {

        override fun getFamilyName(): String = KotlinBundle.message("simplify.negated.operation")

        override fun getName(): String {
            val expression =  KtPsiUtil.deparenthesize(element.baseExpression) as? KtOperationExpression ?: return familyName
            val operation = expression.operationReference.getReferencedNameElementType() as? KtSingleValueToken ?: return familyName
            val negatedOperation = operation.negate() ?: return familyName
            val message = if (element.canBeSimplifiedWithoutChangingSemantics())
                "replace.negated.0.operation.with.1" else
                "replace.negated.0.operation.with.1.may.change.semantics.with.floating.point.types"
            return KotlinBundle.message(message, operation.value, negatedOperation.value)
        }

        override fun applyFix(project: Project, element: KtPrefixExpression, updater: ModPsiUpdater) {
            element.simplify()
        }
    }
}