// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.psi

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * If [this] is [KtBlockExpression], returns, after deparenthesizing, a single block statement or `null` if multiple statements are present.
 * Otherwise, simply returns deparenthesized [this].
 */
fun KtExpression.getSingleUnwrappedStatement(): KtExpression? {
    val innerExpression = KtPsiUtil.safeDeparenthesize(this, true)

    if (innerExpression is KtBlockExpression) {
        val statement = innerExpression.statements.singleOrNull() ?: return null
        val deparenthesized = KtPsiUtil.safeDeparenthesize(statement, true)
        if (deparenthesized is KtLambdaExpression) return null
        return deparenthesized
    }

    return innerExpression
}

/**
 * See [getSingleUnwrappedStatement].
 */
fun KtExpression.getSingleUnwrappedStatementOrThis(): KtExpression = getSingleUnwrappedStatement() ?: this

fun KtBinaryExpression.expressionComparedToNull(): KtExpression? {
    val operationToken = this.operationToken
    if (operationToken != KtTokens.EQEQ && operationToken != KtTokens.EXCLEQ) return null

    val right = this.right ?: return null
    val left = this.left ?: return null

    val rightIsNull = right.isNullExpression()
    val leftIsNull = left.isNullExpression()
    if (leftIsNull == rightIsNull) return null
    return if (leftIsNull) right else left
}

fun KtExpression?.isNullExpression(): Boolean = this?.getSingleUnwrappedStatementOrThis()?.node?.elementType == KtNodeTypes.NULL