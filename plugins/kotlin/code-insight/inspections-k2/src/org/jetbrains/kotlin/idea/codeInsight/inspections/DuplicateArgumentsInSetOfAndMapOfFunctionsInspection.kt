// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor

internal class DuplicateArgumentsInSetOfAndMapOfFunctionsInspection :
    KotlinApplicableInspectionBase<KtCallExpression, DuplicateArgumentsInSetOfAndMapOfFunctionsInspection.Context>() {

    data class Context(
        val duplicates: Map<Any?, List<SmartPsiElementPointer<KtExpression>>>,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor { call ->
        visitTargetElement(call, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val functionName = (element.calleeExpression as? KtNameReferenceExpression)?.text
        return functionName in mapFunctionNames || functionName in setFunctionNames
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val duplicates = getDuplicateArguments(element).ifEmpty { return null }
        return Context(duplicates)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("inspection.duplicate.arguments.in.setof.mapof.functions.display.message", context.duplicates.keys.first() ?: "null"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
        *LocalQuickFix.EMPTY_ARRAY
    )

    override fun registerProblem(
        ranges: List<TextRange>,
        holder: ProblemsHolder,
        element: KtCallExpression,
        context: Context,
        isOnTheFly: Boolean,
    ) {
        val elementStart = element.textRange.startOffset
        context.duplicates.forEach { (key, duplicates) ->
            duplicates.forEach { expr ->
                val rangeInElement = expr.element?.textRange?.shiftLeft(elementStart) ?: return@forEach
                holder.manager.createProblemDescriptor(
                    element,
                    rangeInElement,
                    KotlinBundle.message("inspection.duplicate.arguments.in.setof.mapof.functions.display.message", key ?: "null"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    *LocalQuickFix.EMPTY_ARRAY
                ).takeUnless { problemDescriptor ->
                    problemDescriptor.highlightType == ProblemHighlightType.INFORMATION && !isOnTheFly
                }?.let(holder::registerProblem)
            }
        }
    }

    private fun KaSession.getDuplicateArguments(element: KtCallExpression): Map<Any?, List<SmartPsiElementPointer<KtExpression>>> {
        val callableId = element.resolveToCall()
            ?.singleFunctionCallOrNull()
            ?.symbol
            ?.callableId
            ?: return emptyMap()

        if (callableId.packageName != StandardClassIds.BASE_COLLECTIONS_PACKAGE) return emptyMap()

        val consts = mutableMapOf<Any?, MutableList<SmartPsiElementPointer<KtExpression>>>()
        val functionName = callableId.callableName.asString()

        if (functionName in mapFunctionNames) {
            element.valueArguments.forEach { valueArg ->
                val argumentExpression = valueArg.getArgumentExpression() ?: return@forEach
                if (argumentExpression is KtBinaryExpression) {
                    val constantValue = argumentExpression.left?.evaluate() ?: return@forEach
                    consts.getOrPut(constantValue.value) { mutableListOf() }.add(SmartPointerManager.createPointer(argumentExpression))
                }
            }
        } else if (functionName in setFunctionNames) {
            element.valueArguments.forEach { valueArg ->
                val argumentExpression = valueArg.getArgumentExpression()
                val constantValue = argumentExpression?.evaluate() ?: return@forEach
                consts.getOrPut(constantValue.value) { mutableListOf() }.add(SmartPointerManager.createPointer(argumentExpression))

            }
        }

        return consts.filterValues { it.size > 1 }
    }
}

private val mapFunctionNames: Set<String> = setOf(
    "mapOf", "mutableMapOf", "hashMapOf", "linkedMapOf", "sortedMapOf",
)
private val setFunctionNames: Set<String> = setOf(
    "setOf", "mutableSetOf", "hashSetOf", "linkedSetOf", "sortedSetOf",
)