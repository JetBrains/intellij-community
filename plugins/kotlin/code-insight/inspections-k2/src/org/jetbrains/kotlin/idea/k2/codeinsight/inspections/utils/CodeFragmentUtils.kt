// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

/**
 * Creates a code fragment for testing if a function resolves to stdlib, handling special syntax transformations.
 * 'with' uses different syntax: with(receiver) vs receiver.function, so we need special handling.
 */
internal fun createFragmentForSyntaxTransformation(
    expression: KtCallExpression,
    calleeName: String, 
    targetName: String,
    factory: KtPsiFactory
): KtExpressionCodeFragment? {
    val lambda = expression.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return null
    
    return when {
        calleeName == "with" -> createFragmentFromWithFunction(expression, targetName, lambda, factory)
        targetName == "with" -> createFragmentToWithFunction(expression, targetName, lambda, factory)
        else -> null // No special syntax transformation needed
    }
}

/**
 * Creates a fragment when converting FROM 'with' function: with(receiver) -> receiver.targetName
 */
internal fun createFragmentFromWithFunction(
    expression: KtCallExpression,
    targetName: String,
    lambda: KtLambdaExpression,
    factory: KtPsiFactory
): KtExpressionCodeFragment? {
    val receiver = expression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
    // When converting from 'with', the target function uses parameters, so we keep the lambda as-is
    val newCallExpression = factory.createExpressionByPattern("$0.$1$2", receiver.text, targetName, lambda.text)
    return factory.createExpressionCodeFragment(newCallExpression.text, expression.parent)
}

/**
 * Creates a fragment when converting TO 'with' function: receiver.calleeName -> with(receiver)
 */
internal fun createFragmentToWithFunction(
    expression: KtCallExpression,
    targetName: String,
    lambda: KtLambdaExpression,
    factory: KtPsiFactory
): KtExpressionCodeFragment? {
    val qualifiedExpression = expression.parent as? KtQualifiedExpression ?: return null
    val receiver = qualifiedExpression.receiverExpression
    // When converting to 'with', we need to remove parameters since 'with' uses implicit 'this'
    val lambdaText = (lambda.copy() as? KtLambdaExpression)?.let { lambdaCopy ->
        lambdaCopy.functionLiteral.valueParameterList?.delete()
        lambdaCopy.text
    } ?: lambda.text
    val newCallExpression = factory.createExpressionByPattern("$0($1)$2", targetName, receiver.text, lambdaText)
    return factory.createExpressionCodeFragment(newCallExpression.text, expression.parent?.parent)
}

/**
 * Checks if the given name resolves to a standard library function.
 * This is done by creating a code fragment with the counterpart name and checking if it resolves to a function in the Kotlin package.
 *
 * @param expression The original call expression
 * @param calleeName The name of the original function
 * @param name The name of the counterpart function
 * @return True if the counterpart name resolves to a standard library function
 */
internal fun nameResolvesToStdlib(expression: KtCallExpression, calleeName: String, name: String): Boolean {
    val factory = KtPsiFactory(expression.project)

    // Handle special syntax transformations (like 'with' conversions)
    val fragment = createFragmentForSyntaxTransformation(expression, calleeName, name, factory)
        ?: run {
            val parentExpression = expression.parent
            val newExpression = when (parentExpression) {
                is KtQualifiedExpression -> {
                    // Handle qualified expressions like "receiver.function { ... }" or "receiver?.function { ... }"
                    val copy = parentExpression.copy() as KtQualifiedExpression
                    (copy.selectorExpression as? KtCallExpression)?.calleeExpression?.replace(factory.createExpression(name))
                    copy
                }

                else -> {
                    // Handle simple call expressions like "function { ... }"
                    val copy = expression.copy() as KtCallExpression
                    copy.calleeExpression?.replace(factory.createExpression(name))
                    copy
                }
            }
            newExpression.let { factory.createExpressionCodeFragment(it.text, parentExpression) }
        }
    return analyze(fragment) {
        // Resolve the symbol for the counterpart function
        val callableSymbol: KaCallableSymbol? = when (val fragmentExpression: KtExpression? = fragment.getContentElement()) {
            is KtDotQualifiedExpression -> {
                // Handle qualified expressions like "receiver.function()"
                val callExpression = fragmentExpression.selectorExpression as? KtCallExpression
                callExpression?.calleeExpression?.mainReference?.resolveToSymbol() as? KaCallableSymbol
            }
            else -> {
                // Handle other expressions
                val resolvedFragmentCall = fragmentExpression?.resolveToCall()?.successfulCallOrNull<KaCall>()
                (resolvedFragmentCall as? KaCallableMemberCall<*, *>)?.partiallyAppliedSymbol?.symbol
            }
        }

        // Check if the symbol is from the Kotlin standard library
        callableSymbol != null &&
                callableSymbol.callableId?.packageName == StandardClassIds.BASE_KOTLIN_PACKAGE &&
                callableSymbol.callableId?.callableName?.asString() == name
    }
}