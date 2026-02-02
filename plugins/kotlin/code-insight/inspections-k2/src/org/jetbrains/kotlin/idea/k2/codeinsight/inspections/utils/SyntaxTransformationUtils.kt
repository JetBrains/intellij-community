// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
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