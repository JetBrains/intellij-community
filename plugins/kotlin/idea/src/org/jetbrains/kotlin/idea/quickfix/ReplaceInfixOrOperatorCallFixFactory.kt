// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.resolve.calls.resolvedCallUtil.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.expressions.OperatorConventions

object ReplaceInfixOrOperatorCallFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val expression = diagnostic.psiElement
        if (expression is KtArrayAccessExpression && diagnostic.factory != Errors.UNSAFE_IMPLICIT_INVOKE_CALL) {
            if (expression.arrayExpression == null) return null
            return ReplaceInfixOrOperatorCallFix(expression, expression.shouldHaveNotNullType())
        }

        when (val parent = expression.parent) {
            is KtBinaryExpression -> {
                return when {
                    parent.left == null || parent.right == null -> null
                    parent.operationToken == KtTokens.EQ -> null
                    parent.operationToken in OperatorConventions.COMPARISON_OPERATIONS -> null
                    else -> {
                        val binaryOperatorName = if (parent.operationToken == KtTokens.IDENTIFIER) {
                            // Get name of infix function call
                            parent.operationReference.text
                        } else {
                            parent.resolveToCall(BodyResolveMode.FULL)?.candidateDescriptor?.name?.asString()
                        }
                        binaryOperatorName?.let {
                            ReplaceInfixOrOperatorCallFix(parent, parent.shouldHaveNotNullType(), binaryOperatorName)
                        }
                    }
                }
            }
            is KtCallExpression -> {
                if (parent.calleeExpression == null || parent.valueArgumentList == null) return null
                val resolvedCall = parent.resolveToCall(BodyResolveMode.FULL) ?: return null
                if (!resolvedCall.canBeReplacedWithInvokeCall() || resolvedCall.getImplicitReceiverValue() != null) return null
                return ReplaceInfixOrOperatorCallFix(parent, parent.shouldHaveNotNullType())
            }
            else -> return null
        }
    }
}
