// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.utils.addToStdlib.constant

interface ReadWriteAccessChecker {
    fun readWriteAccessWithFullExpressionByResolve(assignment: KtBinaryExpression): Pair<ReferenceAccess, KtExpression>?

    fun readWriteAccessWithFullExpression(
        targetExpression: KtExpression,
        useResolveForReadWrite: Boolean
    ): Pair<ReferenceAccess, KtExpression> {
        var expression = targetExpression.getQualifiedExpressionForSelectorOrThis()
        loop@ while (true) {
            when (val parent = expression.parent) {
                is KtParenthesizedExpression, is KtAnnotatedExpression, is KtLabeledExpression -> expression = parent as KtExpression
                else -> break@loop
            }
        }

        val assignment = expression.getAssignmentByLHS()
        if (assignment != null) {
            return when (assignment.operationToken) {
                KtTokens.EQ -> ReferenceAccess.WRITE to assignment

                else -> {
                    (if (useResolveForReadWrite) readWriteAccessWithFullExpressionByResolve(assignment) else null)
                        ?: (ReferenceAccess.READ_WRITE to assignment)
                }
            }
        }

        val unaryExpression = expression.parent as? KtUnaryExpression
        return if (unaryExpression != null && unaryExpression.operationToken in constant { setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS) })
            ReferenceAccess.READ_WRITE to unaryExpression
        else
            ReferenceAccess.READ to expression
    }

    companion object {
        fun getInstance(): ReadWriteAccessChecker = service()
    }
}

fun KtExpression.readWriteAccessWithFullExpression(useResolveForReadWrite: Boolean): Pair<ReferenceAccess, KtExpression> =
    ReadWriteAccessChecker.getInstance().readWriteAccessWithFullExpression(this, useResolveForReadWrite)

fun KtExpression.readWriteAccess(useResolveForReadWrite: Boolean): ReferenceAccess =
    ReadWriteAccessChecker.getInstance().readWriteAccessWithFullExpression(this, useResolveForReadWrite).first

