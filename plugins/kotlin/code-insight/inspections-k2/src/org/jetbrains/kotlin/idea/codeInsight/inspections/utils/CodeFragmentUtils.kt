// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.utils

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

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

internal fun buildCodeFragmentWithCollectionLiteral(
    element: KtCallExpression,
    context: KtExpression,
): KtCollectionLiteralExpression? {
    val elementRange = element.textRange
    val contextStartOffset = context.textRange.startOffset
    val startIndex = elementRange.startOffset - contextStartOffset
    val endIndex = elementRange.endOffset - contextStartOffset
    val literalText = element.toCollectionLiteralString() ?: return null
    val textWithLiteral = context.text.replaceRange(startIndex, endIndex, literalText)
    val codeFragment = KtPsiFactory(element.project).createBlockCodeFragment(textWithLiteral, context)
    return codeFragment.findElementAt(startIndex)?.parentOfType()
}

@OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
internal fun isCollectionLiteralSafeAsArgument(
    element: KtCallExpression,
    expressionType: KaType,
): Boolean {
    val context = findContextToAnalyze(element) ?: return false

    val literal = buildCodeFragmentWithCollectionLiteral(element, context) ?: return false

    val expressionTypePointer = expressionType.createPointer()
    return analyze(literal) {
        val restoredType = expressionTypePointer.restore(this) ?: return false
        val literalType = literal.expressionType ?: return false
        if (!literalType.semanticallyEquals(restoredType)) return false
        val outerCall = literal.getParentOfType<KtCallExpression>(strict = true) ?: return true
        outerCall.resolveCall() != null
    }
}

private fun findContextToAnalyze(expression: KtExpression): KtExpression? {
    for (element in expression.parentsWithSelf) {
        when (element) {
            is KtFunctionLiteral -> continue
            is KtParameter -> continue
            is KtPropertyAccessor -> continue
            is KtProperty -> if (element.parent is KtClassBody) continue else return element
            is KtFunction -> if (element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) continue else return element
            is KtDeclaration -> return element
            else -> continue
        }
    }
    return null
}