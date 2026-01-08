// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParenthesizerOrThis

internal class RedundantReturnKeywordInspection : KotlinApplicableInspectionBase.Simple<KtReturnExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = returnExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtReturnExpression,
        context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("inspection.redundant.return.keyword.display.name")

    override fun getApplicableRanges(element: KtReturnExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.returnKeyword }

    override fun isApplicableByPsi(element: KtReturnExpression): Boolean {
        if (element.getLabelName() != null) return false

        val returnedExpression = element.returnedExpression
        if (returnedExpression is KtLabeledExpression && returnedExpression.baseExpression is KtLambdaExpression) {
            // See the following test cases:
            // - labeledLambdaAfterCall.kt
            // - labeledLambdaAfterVariableInitializerCall.kt
            // where removing the 'return' keyword results in red code.
            //
            // To produce a correct quick fix, the labeled lambda must either be wrapped in parentheses
            // or followed by a semicolon after the previous sibling.
            //
            // However, it is preferable to treat this as a compiler-side issue, since
            // `org.jetbrains.kotlin.psi.KtExpressionImpl.Companion.replaceExpression` already handles cases
            // where parentheses are necessary for lambdas. See the following test cases:
            // - lambdaAfterCall.kt
            // - lambdaAfterVariableInitializerCall.kt
            //
            // The compiler should also support labeled lambdas in the same way.
            // TODO: remove this `if` check and `// IGNORE_K2` in the test data once labeled lambdas are supported on the compiler side.
            return false
        }

        val topMostOwner = generateSequence<KtExpression>(element) { findOwner(it) }.lastOrNull() ?: return false
        val normalizedTopMostOwner = topMostOwner.getOutermostParenthesizerOrThis()

        return normalizedTopMostOwner.parent is KtReturnExpression
    }

    /**
     * When a `return` is unreachable,
     * [org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.KotlinUnreachableCodeInspection]
     * already suggests a quick fix removing `return`, so [RedundantReturnKeywordInspection] does not trigger,
     * avoiding duplicate fixes.
     *
     * @see org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.RedundantReturnKeyword.testUnreachableReturn
     * @see org.jetbrains.kotlin.idea.k2.inspections.tests.K2LocalInspectionTestGenerated.RedundantReturnKeyword.testUnreachableThrow
     */
    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtReturnExpression): Unit? =
        element.diagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
            .none { it is KaFirDiagnostic.UnreachableCode }
            .asUnit

    override fun createQuickFix(
        element: KtReturnExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtReturnExpression> = object : KotlinModCommandQuickFix<KtReturnExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.redundant.return.keyword.action.name")

        override fun applyFix(
            project: Project,
            element: KtReturnExpression,
            updater: ModPsiUpdater,
        ) {
            val returnExpression = element.returnedExpression
            if (returnExpression == null) {
                element.delete()
            } else {
                val commentSaver = CommentSaver(element)
                val replaced = element.replace(returnExpression)
                commentSaver.restore(replaced)
            }
        }
    }
}

private fun findOwner(expression: KtExpression): KtExpression? {
    val normalizedExpression = expression.getOutermostParenthesizerOrThis()

    return when (val container = normalizedExpression.parent) {
        is KtContainerNodeForControlStructureBody -> {
            if (container.expression !== normalizedExpression) return null
            getTopmostIf(container)
        }

        is KtWhenEntry -> {
            if (container.expression !== normalizedExpression) return null
            container.parent as? KtWhenExpression
        }

        is KtBlockExpression -> {
            if (container.statements.lastOrNull() !== normalizedExpression) return null
            when (val parent = container.parent) {
                is KtContainerNodeForControlStructureBody -> getTopmostIf(parent)
                is KtWhenEntry -> parent.parent as? KtWhenExpression
                else -> null
            }
        }

        // Handle Elvis operator: return value ?: return "default"
        // BUT exclude: return value ?: return null (handled by RedundantElvisReturnNullInspection)
        is KtBinaryExpression -> container.takeIf {
            it.operationToken == KtTokens.ELVIS
                && it.right === normalizedExpression
                && !(normalizedExpression as? KtReturnExpression)?.returnedExpression.isNullExpression()
        }

        else -> null
    }
}

private fun getTopmostIf(element: KtContainerNodeForControlStructureBody): KtIfExpression? {
    val ifExpression = element.parent as? KtIfExpression ?: return null
    return getTopmostIf(ifExpression)
}

private fun getTopmostIf(element: KtIfExpression?): KtIfExpression? {
    if (element == null) return element
    val parent = element.parent as? KtContainerNodeForControlStructureBody ?: return element
    val grandParent = parent.parent as? KtIfExpression ?: return element
    return getTopmostIf(grandParent)
}
