// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.utils

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

/**
 * Applies the FROM 'with' conversion: with(receiver) -> receiver.counterpartName
 */
internal fun applyFromWithConversion(
    expression: KtCallExpression,
    element: KtNameReferenceExpression,
    counterpartName: String,
    factory: KtPsiFactory
): Boolean {
    val receiver = expression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
    // Remove the entire value argument list "(receiver)" to avoid empty parentheses
    expression.valueArgumentList?.delete()
    // Then replace "with" with "receiver.counterpartName"
    element.replace(factory.createExpressionByPattern("$0.$1", receiver.text, counterpartName))
    return true
}

/**
 * Applies the TO 'with' conversion: receiver.originalCalleeName -> with(receiver)
 */
internal fun applyToWithConversion(
    element: KtNameReferenceExpression,
    counterpartName: String,
    lambda: KtLambdaArgument,
    factory: KtPsiFactory
): KtCallExpression? {
    val qualifiedExpression = element.parent?.parent as? KtQualifiedExpression ?: return null
    val receiver = qualifiedExpression.receiverExpression
    
    // Create the complete with call including the modified lambda
    val newWithCall = factory.createExpressionByPattern(
        "$0($1)$2", 
        counterpartName, 
        receiver.text, 
        lambda.text
    )
    return qualifiedExpression.replace(newWithCall) as KtCallExpression
}

internal fun KtCallExpression.toCollectionLiteralString(): String? {
    val regularArgsContent = valueArgumentList?.text?.run { drop(1).dropLast(1) } ?: ""
    val lambdaArgTexts = lambdaArguments.map { it.getLambdaExpression()?.text ?: return null }
    val lambdaArgsContent = lambdaArgTexts.joinToString(", ")
    val allArgs = listOf(regularArgsContent, lambdaArgsContent).filter { it.isNotEmpty() }.joinToString(", ")
    if (allArgs.isEmpty() && valueArgumentList == null) return null
    return "[$allArgs]"
}

internal val TARGET_FUNCTION_FQ_NAMES: Set<FqName> = setOf(
    FqName("kotlin.collections.listOf"),
    FqName("kotlin.collections.emptyList"),
    FqName("kotlin.collections.mutableListOf"),
    FqName("kotlin.collections.setOf"),
    FqName("kotlin.collections.emptySet"),
    FqName("kotlin.collections.mutableSetOf"),
    FqName("kotlin.sequences.sequenceOf"),
    FqName("kotlin.sequences.emptySequence"),
    FqName("kotlin.arrayOf"),
    FqName("kotlin.emptyArray"),
    FqName("kotlin.booleanArrayOf"),
    FqName("kotlin.byteArrayOf"),
    FqName("kotlin.charArrayOf"),
    FqName("kotlin.doubleArrayOf"),
    FqName("kotlin.floatArrayOf"),
    FqName("kotlin.intArrayOf"),
    FqName("kotlin.shortArrayOf"),
    FqName("kotlin.longArrayOf"),
    FqName("kotlin.ubyteArrayOf"),
    FqName("kotlin.uintArrayOf"),
    FqName("kotlin.ulongArrayOf"),
    FqName("kotlin.ushortArrayOf"),
)

internal val LITERAL_TO_FUNCTION_CANDIDATES: List<FqName> = TARGET_FUNCTION_FQ_NAMES.filter {
    it != FqName("kotlin.collections.emptyList") && it != FqName("kotlin.collections.emptySet")
}