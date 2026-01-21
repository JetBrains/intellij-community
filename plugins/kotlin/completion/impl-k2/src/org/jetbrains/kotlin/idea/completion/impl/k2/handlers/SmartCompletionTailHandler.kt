// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isFunctionType
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.completion.handlers.WithTailInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinExpressionNameReferencePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

/**
 * Tails are the tail of what should be inserted by smart completion after choosing a completion item to
 * move the user's caret to the place where they will likely need to invoke completion next.
 * In some cases, the tail is already in the code, in which case rather than inserting it,
 * smart completion moves past the existing tail and places the caret after it.
 * Otherwise, the tail is inserted and the caret is moved to be after the tail.
 *
 * For example, in the following code:
 * ```
 * fun foo(a: Int, b: Int) {}
 * fun bar(a: Int, b: Int) {
 *     foo(<caret>)
 * }
 * ```
 * Smart completion knows that after completing the first parameter for `foo`, another argument will be
 * required so the tail will be `, ` and the resulting code will be:
 * ```
 * fun foo(a: Int, b: Int) {}
 * fun bar(a: Int, b: Int) {
 *     foo(a, <caret>)
 * }
 * ```
 */
private enum class Tail {
    COMMA, // Additional argument to call required
    RPARENTH, // Call needs no more arguments, move past the closing parenthesis
    RBRACKET, // Similar as RPARENTH but for array access
    ELSE, // When an if expression is completed and an `else` branch is required
    RBRACE // For lambda parameters
}

/**
 * Returns the tail for a named argument
 */
private fun namedArgumentTail(
    argumentName: Name,
    call: KaFunctionCall<*>,
): Tail? {
    val usedParameterNames = (call.argumentMapping.values.map { it.name } + listOf(argumentName)).toSet()
    val notUsedParameters = call.signature.valueParameters.filter { it.name !in usedParameterNames }
    return when {
        notUsedParameters.isEmpty() -> Tail.RPARENTH // named arguments no supported for []
        notUsedParameters.all { it.symbol.hasDefaultValue } -> null
        else -> Tail.COMMA
    }
}

/**
 * Returns the tail for this [call] belonging to the [argumentExpression].
 * Returns a single tail if a tail could be determined, or null otherwise.
 */
context(_: KaSession)
private fun calculateTailForCall(argumentExpression: KtExpression, call: KaCall): Tail? {
    if (call !is KaFunctionCall<*>) return null
    if (call.argumentMapping.isEmpty()) return null

    val argument = argumentExpression.parent ?: return null
    val argumentName = (argument as? KtValueArgument)?.getArgumentName()?.asName
    val isFunctionLiteralArgument = argument is LambdaArgument

    var parameter = call.argumentMapping[argumentExpression]

    val isArrayAccess = argument.parent is KtArrayAccessExpression
    val rparenthTail = if (isArrayAccess) Tail.RBRACKET else Tail.RPARENTH

    var parameters = call.signature.valueParameters.toList()
    if (isArrayAccess && call.argumentMapping.size == 2) {
        // last parameter in set is used for value assigned
        if (parameter == parameters.last()) {
            parameter = null
        }
        parameters = parameters.dropLast(1)
    }

    if (parameter == null) {
        return null
    }

    fun needCommaForParameter(parameter: KaVariableSignature<KaValueParameterSymbol>): Boolean {
        if (parameter.symbol.hasDefaultValue) return false // parameter is optional
        if (parameter.symbol.isVararg) return false // vararg arguments list can be empty
        // last parameter of functional type can be placed outside parenthesis:
        if (!isArrayAccess && parameter == parameters.last() && parameter.returnType.isFunctionType) return false
        return true
    }

    val tail = if (argumentName == null) {
        when {
            parameter == parameters.last() -> rparenthTail
            parameters.dropWhile { it != parameter }.drop(1).any { needCommaForParameter(it) } -> Tail.COMMA
            else -> null
        }
    } else {
        namedArgumentTail(argumentName, call)
    }

    val alreadyHasStar = (argument as? KtValueArgument)?.getSpreadElement() != null
    return if (parameter.symbol.isVararg) {
        if (isFunctionLiteralArgument) return null

        val varargTail = if (argumentName == null && tail == rparenthTail) {
            null /* even if it's the last parameter, there can be more arguments for the same parameter */
        } else tail

        varargTail?.takeIf { !alreadyHasStar }
    } else {
        if (alreadyHasStar) return null
        tail.takeIf { !isFunctionLiteralArgument }
    }
}

/**
 * Calculates all tails based on all possible call candidates at the position.
 */
context(_: KaSession, _: K2CompletionSectionContext<*>)
private fun calculateTailForCalls(
    expression: KtExpression,
    argument: KtElement,
    argumentParent: PsiElement,
): Set<Tail> {
    // The way of getting the callCandidates depends on what kind of position we are in
    val callCandidates = when {
        argument is KtValueArgument -> {
            val argumentList = argument.parent as? KtValueArgumentList ?: return emptySet()
            val call = argumentList.parent as? KtCallElement ?: return emptySet()
            call.resolveToCallCandidates()
        }

        argumentParent is KtArrayAccessExpression -> {
            argumentParent.resolveToCallCandidates()
        }

        else -> emptyList()
    }

    if (callCandidates.isEmpty()) return emptySet()

    return callCandidates.mapNotNullTo(mutableSetOf()) {
        calculateTailForCall(expression, it.candidate)
    }
}

context(_: KaSession, context: K2CompletionSectionContext<*>)
private fun calculateTailForIfExpression(
    argumentExpression: KtExpression,
    ifExpression: KtIfExpression
): Set<Tail> {
    return when (argumentExpression) {
        // If we are inside the condition, we need to close the condition's parenthesis.
        ifExpression.condition -> setOf(Tail.RPARENTH)
        // If we are done with the `then` block, add `else` and move into it
        ifExpression.then -> setOf(Tail.ELSE)
        // If we completed the `else`, the `if` is effectively done and we use the tail that should
        // be used for the surrounding if expression.
        ifExpression.`else` -> ifExpression.calculateTails()
        else -> emptySet()
    }
}

context(_: KaSession, context: K2CompletionSectionContext<*>)
private fun calculateTailForBinaryExpression(
    argumentExpression: KtExpression,
    binaryExpression: KtBinaryExpression,
): Set<Tail> {
    if (argumentExpression != binaryExpression.right) return emptySet()
    // If we completed the binary expression, we are done with it, and we use the
    // tail from the surrounding expression to move to the correct place.
    return binaryExpression.calculateTails()
}

context(_: KaSession, _: K2CompletionSectionContext<*>)
private fun calculateTailForBlockExpression(
    blockExpression: KtBlockExpression,
): Set<Tail> {
    if (blockExpression.parent !is KtFunctionLiteral) return emptySet()
    // We completed the last expression inside a function literal, which
    // means we are done with the function literal and can move past it.
    return setOf(Tail.RBRACE)
}


/**
 * Calculates the expected tail to be used in smart completion for the given expression.
 * See [Tail] for details.
 *
 * The majority of the tail logic has been ported from K1 code from:
 * `org.jetbrains.kotlin.idea.core.ExpectedInfos`
 */
context(_: KaSession, context: K2CompletionSectionContext<*>)
private fun KtExpression.calculateTails(): Set<Tail> {
    val argumentExpression = getQualifiedExpressionForSelectorOrThis()
    val argument = argumentExpression.parent as? KtElement ?: return emptySet()
    val argumentParent = argument.parent ?: return emptySet()

    return when {
        argumentParent is KtIfExpression -> {
            calculateTailForIfExpression(argumentExpression, argumentParent)
        }

        argument is KtBinaryExpression -> {
            calculateTailForBinaryExpression(argumentExpression, argument)
        }

        argument is KtBlockExpression -> {
            calculateTailForBlockExpression(argument)
        }

        else -> {
            calculateTailForCalls(argumentExpression, argument, argumentParent)
        }
    }
}

private fun Tail.getInsertHandler(): WithTailInsertHandler = when (this) {
    Tail.COMMA -> WithTailInsertHandler.COMMA
    Tail.RPARENTH -> WithTailInsertHandler.RPARENTH
    Tail.RBRACKET -> WithTailInsertHandler.RBRACKET
    Tail.ELSE -> WithTailInsertHandler.ELSE
    Tail.RBRACE -> WithTailInsertHandler.RBRACE
}

/**
 * Adds an insertion handler to the element that inserts or moves past (if it already exists)
 * a tail depending on the calls available at the position.
 *
 * See [Tail] for more detailed documentation.
 */
context(_: KaSession, context: K2CompletionSectionContext<*>)
internal fun LookupElement.addSmartCompletionTailInsertHandler(): LookupElement {
    val positionContext = context.positionContext
    if (positionContext !is KotlinExpressionNameReferencePositionContext) return this

    // There might be multiple tails depending on all the available signatures.
    // If all call signatures require the same tail (i.e., the set is a singleton), we can use this single tail.
    val singleTail = positionContext.nameExpression.calculateTails().singleOrNull()

    return if (singleTail != null) {
        LookupElementDecorator.withDelegateInsertHandler(this, singleTail.getInsertHandler())
    } else {
        this
    }
}