// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.lang.BinaryOperationPrecedence
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression

object PsiPrecedences {
    private val LOG = Logger.getInstance(PsiPrecedences::class.java)

    private const val PRECEDENCE_OF_ATOMIC_EXPRESSION: Int = -1

    fun getPrecedence(expression: KtExpression): Int {
        return when (expression) {
            is KtAnnotatedExpression,
            is KtLabeledExpression,
            is KtPostfixExpression -> 0
            is KtPrefixExpression -> 1
            is KtOperationExpression -> {
                val operation = expression.operationReference.getReferencedNameElementType()
                val binaryOperationPrecedence = BinaryOperationPrecedence.TOKEN_TO_BINARY_PRECEDENCE_MAP[operation]
                if (binaryOperationPrecedence == null) {
                    LOG.error("No precedence for operation: $operation")
                    14 // Number of unary (2) and binary (12) precedences
                } else {
                    binaryOperationPrecedence.ordinal
                }
            }
            else -> PRECEDENCE_OF_ATOMIC_EXPRESSION
        }
    }

    fun isTighter(subject: Int, tighterThan: Int): Boolean {
        return subject < tighterThan
    }
}