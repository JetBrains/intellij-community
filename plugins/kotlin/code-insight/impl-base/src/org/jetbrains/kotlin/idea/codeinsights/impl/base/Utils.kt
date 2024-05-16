// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.appendDotQualifiedSelector
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver

fun KtExpression.isComplexInitializer(): Boolean {
    fun KtExpression.isElvisExpression(): Boolean = this is KtBinaryExpression && operationToken == KtTokens.ELVIS

    if (!isOneLiner()) return true
    return anyDescendantOfType<KtExpression> {
        it is KtThrowExpression || it is KtReturnExpression || it is KtBreakExpression ||
                it is KtContinueExpression || it is KtIfExpression || it is KtWhenExpression ||
                it is KtTryExpression || it is KtLambdaExpression || it.isElvisExpression()
    }
}


fun KtCallableDeclaration.hasUsages(inElement: KtElement): Boolean {
    assert(inElement.isPhysical)
    return hasUsages(listOf(inElement))
}

fun KtCallableDeclaration.hasUsages(inElements: Collection<KtElement>): Boolean {
    assert(this.isPhysical)
    return ReferencesSearch.search(this, LocalSearchScope(inElements.toTypedArray())).any()
}

fun KtExpression.isExitStatement(): Boolean =
    this is KtContinueExpression || this is KtBreakExpression || this is KtThrowExpression || this is KtReturnExpression

fun KtExpression.isSimplifiableTo(other: KtExpression): Boolean = this.getSingleUnwrappedStatementOrThis().text == other.text

fun KtExpression.wrapWithLet(
    receiverExpression: KtExpression,
    expressionsToReplaceWithLambdaParameter: List<KtExpression>
): KtExpression {
    val factory = KtPsiFactory(project)

    val implicitParameterName = StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
    val lambdaParameterName = KotlinNameSuggester.suggestNameByName(implicitParameterName) { candidate ->
        collectDescendantsOfType<KtNameReferenceExpression> { it.text == candidate }.isEmpty()
    }

    for (expressionToReplace in expressionsToReplaceWithLambdaParameter) {
        expressionToReplace.replace(factory.createExpression(lambdaParameterName))
    }
    val lambdaParameterPattern = if (lambdaParameterName != implicitParameterName) "$lambdaParameterName -> " else ""

    return factory.createExpressionByPattern("$0?.let { $lambdaParameterPattern$1 }", receiverExpression, this)
}

tailrec fun KtExpression.insertSafeCallsAfterReceiver(): KtExpression {
    return when (val qualified = this.getQualifiedExpressionForReceiver()) {
        is KtDotQualifiedExpression -> {
            val factory = KtPsiFactory(project)
            val selector = qualified.selectorExpression ?: return this
            val newQualified = factory.createExpressionByPattern("$0?.$1", qualified.receiverExpression, selector)

            qualified.replaced(newQualified).insertSafeCallsAfterReceiver()
        }

        is KtSafeQualifiedExpression -> qualified.insertSafeCallsAfterReceiver()
        else -> this
    }
}

/**
 * Replaces calls present in [variableCalls] with variable access + `invoke()`, starting with selector of [this] and continuing with calls
 * that follow [this].
 * E.g., if `foo().bar()` from `foo().bar().baz()` is provided, with all calls being variable calls,
 * then the selector `bar()` and the following call `baz()` are replaced, resulting in `foo().bar.invoke().baz.invoke()`.
 */
// TODO: remove this function and replace its usages with `OperatorToFunctionConverter.convert`
tailrec fun KtExpression.replaceVariableCallsWithExplicitInvokeCalls(variableCalls: Set<KtCallExpression>): KtExpression {
    val factory = KtPsiFactory(project)

    val callExpression = when (this) {
        is KtCallExpression -> this
        is KtQualifiedExpression -> selectorExpression as? KtCallExpression
        else -> null
    }
    val valueArgumentList = callExpression?.valueArgumentList

    val newExpression = if (callExpression in variableCalls && valueArgumentList != null) {
        val newInvokeCall = factory.createExpressionByPattern("invoke$0", valueArgumentList.text)

        valueArgumentList.delete()

        this.appendDotQualifiedSelector(selector = newInvokeCall, factory)
    } else this

    val qualified = newExpression.getQualifiedExpressionForReceiver() ?: return newExpression

    return qualified.replaceVariableCallsWithExplicitInvokeCalls(variableCalls)
}