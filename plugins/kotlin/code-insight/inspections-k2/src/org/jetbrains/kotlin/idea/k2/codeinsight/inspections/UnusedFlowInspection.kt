// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStartOffsetIn
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective
import org.jetbrains.kotlin.psi.psiUtil.parents

private val FLOW_CLASS_ID = ClassId.fromString("kotlinx/coroutines/flow/Flow")

internal class UnusedFlowInspection : KotlinApplicableInspectionBase<KtExpression, Unit>() {
    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("flow.constructed.but.not.used"),
        ProblemHighlightType.WARNING,
        onTheFly
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = expressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    private fun KtExpression.isBeingProvidedToCall(): Boolean {
        return parents.takeWhile { it is KtExpression && it !is KtBlockExpression }.any { it is KtValueArgumentList }
    }

    private fun KtExpression.isTopMostExpression(): Boolean {
        return parents.takeWhile { it !is KtBlockExpression && it !is KtStatementExpression }
            .none { it is KtDotQualifiedExpression || it is KtCallExpression || it is KtThisExpression }
    }

    private fun KtExpression.isBeingReturned(): Boolean {
        return parents.takeWhile { it is KtExpression && it !is KtBlockExpression }.any { it is KtReturnExpression }
    }

    /**
     * Here we make sure we only analyze the entire expressions that could be used rather than each part of the calls.
     * This way we only analyze a call chain once and only the full call chain.
     */
    override fun isApplicableByPsi(element: KtExpression): Boolean {
        // Operators should not trigger a warning
        if (element is KtOperationReferenceExpression) return false
        // Assignment operations do use the value, so we can skip this case
        if (element is KtBinaryExpression && element.operationToken == KtTokens.EQ) return false
        // These are not expressions
        if (element is KtLabelReferenceExpression || element.parentOfType<KtValueArgumentName>(withSelf = true) != null) return false
        // If the flow is being returned directly, it is being used
        if (element.isBeingReturned()) return false
        if (element.isInImportDirective() || element.parentOfType<KtPackageDirective>() != null) return false
        // If the flow is being provided to a call, the flow is used, and we do not even have to continue.
        if (element.isBeingProvidedToCall()) return false
        // If the expression is not the top most expression, then we do not want to continue because we want to
        // analyze the top most expression instead.
        if (!element.isTopMostExpression()) return false
        return super.isApplicableByPsi(element)
    }

    /**
     * To reduce visual clutter, we only want to highlight the last call in the chain of the flow (and without lambda argument).
     */
    private fun selectApplicableRanges(parent: KtExpression, current: KtExpression): TextRange? = when (current) {
        is KtCallExpression -> {
            val endElement = current.valueArgumentList
                ?: current.calleeExpression
                ?: return null

            TextRange(0, endElement.endOffset - current.startOffset)
                .shiftRight(current.getStartOffsetIn(parent))
        }

        is KtDotQualifiedExpression -> {
            current.selectorExpression?.let {
                selectApplicableRanges(parent, it)
            }
        }

        else -> null
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> {
        return selectApplicableRanges(element, element)?.let { listOf(it) } ?: super.getApplicableRanges(element)
    }

    override fun KaSession.prepareContext(element: KtExpression): Unit? {
        // We check if the element is a flow value and if it is used as an expression (i.e. its value is used).
        // If it is not used, then the user forgot to collect this flow.
        val returnType = (element.resolveExpression() as? KaCallableSymbol)?.returnType as? KaClassType ?: return null
        if (returnType.classId != FLOW_CLASS_ID) return null
        if (element.isUsedAsExpression) return null
        return Unit
    }
}