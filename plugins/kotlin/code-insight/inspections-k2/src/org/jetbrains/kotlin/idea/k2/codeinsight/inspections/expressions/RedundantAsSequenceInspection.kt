// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import org.jetbrains.kotlin.name.StandardClassIds
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.RemoveExplicitTypeArgumentsUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isCalling
import org.jetbrains.kotlin.idea.k2.refactoring.util.areTypeArgumentsRedundant
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.qualifiedExpressionVisitor

private const val KOTLIN_SEQUENCES_FQ_NAME = "kotlin.sequences.Sequence"

internal class RedundantAsSequenceInspection : KotlinApplicableInspectionBase.Simple<KtQualifiedExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = qualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtQualifiedExpression,
        context: Unit,
    ): String = KotlinBundle.message("inspection.redundant.assequence.call")

    override fun isApplicableByPsi(element: KtQualifiedExpression): Boolean {
        val call = element.callExpression ?: return false
        val callee = call.calleeExpression ?: return false
        return callee.text == "asSequence"
    }

    override fun KaSession.prepareContext(element: KtQualifiedExpression): Unit? {
        val call = element.callExpression ?: return null
        if (!call.isCalling(allowedSequenceFunctionFqNames)) return null
        val functionSymbol = resolveToFunctionSymbol(call) ?: return null
        val receiverType = functionSymbol.receiverType ?: return null

        // Case 1: asSequence() called on a Sequence
        if (receiverType.expandedSymbol?.classId?.asSingleFqName()?.asString() == KOTLIN_SEQUENCES_FQ_NAME) {
            if (call.typeArgumentList != null && !areTypeArgumentsRedundant(call)) return null

            return Unit
        }

        // Case 2: asSequence() called on an Iterable followed by a terminal operation
        if (!receiverType.isSubtypeOf(StandardClassIds.Iterable)) return null
        val parent = element.getQualifiedExpressionForReceiver() ?: return null
        val parentCall = parent.callExpression ?: return null
        if (!isTermination(parentCall)) return null
        val grandParentCall = parent.getQualifiedExpressionForReceiver()?.callExpression
        if (grandParentCall != null && isTransformationOrTermination(grandParentCall)) return null

        return Unit
    }

    override fun createQuickFix(
        element: KtQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtQualifiedExpression> = object : KotlinModCommandQuickFix<KtQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("remove.assequence.call.fix.text")

        override fun applyFix(
            project: Project,
            element: KtQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val commentSaver = CommentSaver(element)
            val replaced = element.replaced(element.receiverExpression)
            commentSaver.restore(replaced)
        }
    }
}

private fun KaSession.areTypeArgumentsRedundant(expression: KtCallExpression): Boolean {
    return RemoveExplicitTypeArgumentsUtils.isApplicableByPsi(expression) &&
            areTypeArgumentsRedundant(expression.typeArgumentList!!)
}

private fun KaSession.isTermination(expression: KtCallExpression): Boolean =
    checkFunctionCall(expression, terminations)

private fun KaSession.isTransformationOrTermination(expression: KtCallExpression): Boolean =
    checkFunctionCall(expression, transformationsAndTerminations)

private fun KaSession.checkFunctionCall(expression: KtCallExpression, nameToFqNameMap: Map<String, FqName>): Boolean {
    val calleeText = expression.calleeExpression?.text ?: return false
    val fqName = nameToFqNameMap[calleeText] ?: return false
    val functionSymbol = resolveToFunctionSymbol(expression) ?: return false
    return functionSymbol.callableId?.asSingleFqName() == fqName
}

private fun KaSession.resolveToFunctionSymbol(expression: KtCallExpression): KaNamedFunctionSymbol? =
    expression.calleeExpression?.mainReference?.resolveToSymbol() as? KaNamedFunctionSymbol

private val collectionTerminationFunctionNames: List<String> = listOf(
    "all",
    "any",
    "asIterable",
    "asSequence",
    "associate",
    "associateBy",
    "associateByTo",
    "associateTo",
    "average",
    "contains",
    "count",
    "elementAt",
    "elementAtOrElse",
    "elementAtOrNull",
    "filterIndexedTo",
    "filterIsInstanceTo",
    "filterNotNullTo",
    "filterNotTo",
    "filterTo",
    "find",
    "findLast",
    "first",
    "firstNotNullOf",
    "firstNotNullOfOrNull",
    "firstOrNull",
    "flatMapTo",
    "flatMapIndexedTo",
    "fold",
    "foldIndexed",
    "groupBy",
    "groupByTo",
    "groupingBy",
    "indexOf",
    "indexOfFirst",
    "indexOfLast",
    "joinTo",
    "joinToString",
    "last",
    "lastIndexOf",
    "lastOrNull",
    "mapIndexedNotNullTo",
    "mapIndexedTo",
    "mapNotNullTo",
    "mapTo",
    "maxOrNull",
    "maxByOrNull",
    "maxWithOrNull",
    "maxOf",
    "maxOfOrNull",
    "maxOfWith",
    "maxOfWithOrNull",
    "minOrNull",
    "minByOrNull",
    "minWithOrNull",
    "minOf",
    "minOfOrNull",
    "minOfWith",
    "minOfWithOrNull",
    "none",
    "partition",
    "reduce",
    "reduceIndexed",
    "reduceIndexedOrNull",
    "reduceOrNull",
    "single",
    "singleOrNull",
    "sum",
    "sumBy",
    "sumByDouble",
    "sumOf",
    "toCollection",
    "toHashSet",
    "toList",
    "toMutableList",
    "toMutableSet",
    "toSet",
    "toSortedSet",
    "unzip",
)

private val collectionTransformationFunctionNames: List<String> = listOf(
    "chunked",
    "distinct",
    "distinctBy",
    "drop",
    "dropWhile",
    "filter",
    "filterIndexed",
    "filterIsInstance",
    "filterNot",
    "filterNotNull",
    "flatMap",
    "flatMapIndexed",
    "flatten",
    "map",
    "mapIndexed",
    "mapIndexedNotNull",
    "mapNotNull",
    "minus",
    "minusElement",
    "onEach",
    "onEachIndexed",
    "plus",
    "plusElement",
    "requireNoNulls",
    "runningFold",
    "runningFoldIndexed",
    "runningReduce",
    "runningReduceIndexed",
    "scan",
    "scanIndexed",
    "sorted",
    "sortedBy",
    "sortedByDescending",
    "sortedDescending",
    "sortedWith",
    "take",
    "takeWhile",
    "windowed",
    "withIndex",
    "zipWithNext",
)

private val allowedSequenceFunctionFqNames: Sequence<FqName> = sequenceOf(
    FqName("kotlin.sequences.asSequence"),
    FqName("kotlin.collections.asSequence"),
)

private val terminations: Map<String, FqName> =
    collectionTerminationFunctionNames.associateWith { FqName("kotlin.sequences.$it") }

private val transformationsAndTerminations: Map<String, FqName> =
    collectionTransformationFunctionNames.associateWith { FqName("kotlin.sequences.$it") } + terminations
