// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.*
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.quickfix.AddReturnToUnusedLastExpressionInFunctionFix
import org.jetbrains.kotlin.psi.*

internal class UnusedExpressionInspection : KotlinApplicableInspectionBase<KtExpression, UnusedExpressionInspection.Context>() {

    @JvmInline
    value class Context(val isQuickFixAvailable: Boolean)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtExpression): Context? {
        if (!isAvailable(element)) return null
        return Context(isQuickFixAvailable(element))
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> =
        ApplicabilityRange.self(element)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = expressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val fixes = if (context.isQuickFixAvailable) {
            arrayOf(AddReturnToUnusedLastExpressionInFunctionFix(element).asQuickFix())
        } else {
            LocalQuickFix.EMPTY_ARRAY
        }

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("inspection.unused.expression.problem.description"),
            /* highlightType = */ ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            /* onTheFly = */ false,
            /* ...fixes = */ *fixes,
        )
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.isAvailable(element: KtExpression): Boolean =
    element.diagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
        .any { it is KaFirDiagnostic.UnusedExpression }


private fun KaSession.isQuickFixAvailable(element: KtExpression): Boolean {
    if (!element.isLastStatementInFunctionBody()) return false

    val function = element.parent?.parent as? KtNamedFunction ?: return false

    val functionReturnType = function.returnType.takeIf { it !is KaErrorType } ?: return false
    val expressionType = element.expressionType?.takeIf { it !is KaErrorType } ?: return false

    return expressionType.isSubtypeOf(functionReturnType)
}

private fun KtExpression.isLastStatementInFunctionBody(): Boolean {
    val body = this.parent as? KtBlockExpression ?: return false
    val last = body.statements.lastOrNull() ?: return false
    return last === this
}
