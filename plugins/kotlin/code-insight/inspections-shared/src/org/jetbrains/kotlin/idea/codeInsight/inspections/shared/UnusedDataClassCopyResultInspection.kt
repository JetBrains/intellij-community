// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

private const val COPY_METHOD_NAME = "copy"

internal class UnusedDataClassCopyResultInspection : KotlinApplicableInspectionBase<KtCallExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.calleeExpression?.text == COPY_METHOD_NAME

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val receiver = resolvedCall.partiallyAppliedSymbol.dispatchReceiver ?: return null
        val classSymbol = receiver.type.symbol as? KaNamedClassSymbol ?: return null

        if (!classSymbol.isData) return null

        return element.getQualifiedExpressionForSelectorOrThis().isUsedAsExpression.not().asUnit
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ element.calleeExpression?.textRangeInParent,
        /* descriptionTemplate = */ KotlinBundle.message("inspection.unused.result.of.data.class.copy"),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
    )
}
