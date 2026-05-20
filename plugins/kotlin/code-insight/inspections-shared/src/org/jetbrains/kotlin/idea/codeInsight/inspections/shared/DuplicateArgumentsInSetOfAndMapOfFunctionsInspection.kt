// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
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
import org.jetbrains.kotlin.psi.KtVisitorVoid

class DuplicateArgumentsInSetOfAndMapOfFunctionsInspection : KotlinApplicableInspectionBase<KtCallExpression, Map<Any?, MutableList<KtExpression>>>() {

    override fun KaSession.prepareContext(element: KtCallExpression): Map<Any?, MutableList<KtExpression>>? {
        return getDuplicateArguments(element).ifEmpty { null }
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        listOf(TextRange(0, element.textLength))

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Map<Any?, MutableList<KtExpression>>,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("inspection.duplicate.arguments.in.setof.mapof.functions.display.message", context.keys.first() ?: "null"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
        *LocalQuickFix.EMPTY_ARRAY
    )

    override fun registerProblem(
        ranges: List<TextRange>,
        holder: ProblemsHolder,
        element: KtCallExpression,
        context: Map<Any?, MutableList<KtExpression>>,
        isOnTheFly: Boolean
    ) {
        val elementStart = element.textRange.startOffset
        context.forEach { (key, duplicates) ->
            duplicates.forEach { expr ->
                val rangeInElement = expr.textRange.shiftLeft(elementStart)
                holder.manager.createProblemDescriptor(
                    element,
                    rangeInElement,
                    KotlinBundle.message("inspection.duplicate.arguments.in.setof.mapof.functions.display.message", key ?: "null"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    isOnTheFly,
                    *LocalQuickFix.EMPTY_ARRAY
                ).takeUnless { it.highlightType == ProblemHighlightType.INFORMATION && !isOnTheFly }
                    ?.let(holder::registerProblem)
            }
        }
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        return (element.calleeExpression as? KtNameReferenceExpression)?.text?.let { acceptableNames.contains(it) } ?: false
    }

    fun KaSession.getDuplicateArguments(element: KtCallExpression): Map<Any?, MutableList<KtExpression>> {
        val function = element.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId ?: return emptyMap()
        if (function.packageName != StandardClassIds.BASE_COLLECTIONS_PACKAGE) {
            return emptyMap()
        }
        val consts = mutableMapOf<Any?, MutableList<KtExpression>>()
        if (function.callableName.asString() in mapsMethods) {
            element.valueArguments.forEach { valueArg ->
                val arg = valueArg.getArgumentExpression() ?: return@forEach
                if (arg is KtBinaryExpression) {
                    (arg.left?.evaluate() ?:return@forEach).value.let { consts.getOrPut(it) {mutableListOf()}.add(arg) }
                }
            }
        } else if (function.callableName.asString() in setsMethods) {
            element.valueArguments.forEach { arg ->
                val argumentExpression = arg.getArgumentExpression()
                (argumentExpression
                    ?.evaluate() ?: return@forEach).value.also{ consts.getOrPut(it) {mutableListOf()}.add(argumentExpression) }

            }
        }

        return consts.filterValues { it.size > 1 }
    }
}

val acceptableNames: Set<String> = hashSetOf(
    "setOf", "mapOf",
     "mutableSetOf", "mutableMapOf",
    "hashSetOf", "hashMapOf", "linkedSetOf", "linkedMapOf",
    "sortedSetOf", "sortedMapOf",
)
val mapsMethods: Set<String> = hashSetOf(
    "mapOf", "mutableMapOf", "hashMapOf", "linkedMapOf", "sortedMapOf",
)
val setsMethods: Set<String> = hashSetOf(
    "setOf", "mutableSetOf", "hashSetOf", "linkedSetOf", "sortedSetOf",
)