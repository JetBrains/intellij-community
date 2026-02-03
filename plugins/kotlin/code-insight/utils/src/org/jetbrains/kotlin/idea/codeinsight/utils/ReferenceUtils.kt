// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.unwrapParenthesesLabelsAndAnnotations

/**
 * Checks if the [reference] points to unary plus or minus operator on an [Int] literal, like `-10` or `+(20)`.
 *
 * Currently, such operators are not properly resolved in K2 Mode (see KT-70774).
 * For all other literals, resolving operators on them works fine.
 */
fun isUnaryOperatorOnIntLiteralReference(reference: KtReference): Boolean {
    val unaryOperationReferenceExpression = reference.element as? KtOperationReferenceExpression ?: return false

    if (unaryOperationReferenceExpression.operationSignTokenType !in arrayOf(KtTokens.PLUS, KtTokens.MINUS)) return false

    val prefixExpression = unaryOperationReferenceExpression.parent as? KtUnaryExpression ?: return false
    val unwrappedBaseExpression = prefixExpression.baseExpression?.unwrapParenthesesLabelsAndAnnotations() ?: return false

    return unwrappedBaseExpression is KtConstantExpression &&
            unwrappedBaseExpression.elementType == KtNodeTypes.INTEGER_CONSTANT
}