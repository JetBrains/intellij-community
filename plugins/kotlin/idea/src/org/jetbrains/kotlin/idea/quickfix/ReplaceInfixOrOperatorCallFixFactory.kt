// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.inspections.collections.isMap
import org.jetbrains.kotlin.idea.intentions.canBeReplacedWithInvokeCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.resolve.calls.util.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object ReplaceInfixOrOperatorCallFixFactory : KotlinSingleIntentionActionFactory() {
    private fun findArrayAccessExpression(expression: PsiElement): KtArrayAccessExpression? {
        return expression.safeAs<KtArrayAccessExpression>() ?: expression.parent?.safeAs<KtArrayAccessExpression>()
    }

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val expression = diagnostic.psiElement
        val arrayAccessExpression = findArrayAccessExpression(expression)
        if (arrayAccessExpression != null && diagnostic.factory != Errors.UNSAFE_IMPLICIT_INVOKE_CALL) {
            if (arrayAccessExpression.arrayExpression == null) return null
            return ReplaceInfixOrOperatorCallFix(arrayAccessExpression, arrayAccessExpression.shouldHaveNotNullType())
        }

        when (val parent = expression.parent) {
            is KtBinaryExpression -> {
                val left = parent.left
                when {
                    left == null || parent.right == null -> return null
                    parent.operationToken == KtTokens.EQ -> return null
                    parent.operationToken in OperatorConventions.COMPARISON_OPERATIONS -> return null
                    else -> {
                        if (parent.operationToken in KtTokens.AUGMENTED_ASSIGNMENTS && left is KtArrayAccessExpression) {
                            val type = left.arrayExpression?.getType(left.analyze()) ?: return null
                            if (type.isMap(type.builtIns) && type.arguments.lastOrNull()?.type?.isNullable() != true) return null
                        }
                        val binaryOperatorName = if (parent.operationToken == KtTokens.IDENTIFIER) {
                            // Get name of infix function call
                            parent.operationReference.text
                        } else {
                            parent.resolveToCall(BodyResolveMode.FULL)?.candidateDescriptor?.name?.asString()
                        }
                        return binaryOperatorName?.let {
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
