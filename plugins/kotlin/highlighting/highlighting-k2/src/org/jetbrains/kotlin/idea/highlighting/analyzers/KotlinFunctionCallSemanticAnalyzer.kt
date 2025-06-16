// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting.analyzers

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.highlighting.KotlinCallHighlighterExtension
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal open class KotlinFunctionCallSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession) : KotlinSemanticAnalyzer(holder, session) {
    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        val operationReference = expression.operationReference as? KtReferenceExpression ?: return
        if (operationReference.isAssignment()) return
        expressionHighlightType(expression)
            ?.let { highlightElement(operationReference, it) }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val callee = expression.calleeExpression ?: return
        if (callee is KtLambdaExpression || callee is KtCallExpression /* KT-16159 */) return

        expressionHighlightType(expression, callee)
            ?.let { highlightElement(callee, it) }
    }

    private fun expressionHighlightType(expression: KtBinaryExpression): HighlightInfoType? {
        with(session) {
            val call = expression.resolveToCall()?.successfulCallOrNull<KaCall>() ?: return null
            if (call is KaSimpleFunctionCall && (call.symbol as? KaNamedFunctionSymbol)?.isOperator == true) return null
            val highlightInfoType = getDefaultHighlightInfoTypeForCall(call)
            return highlightInfoType
        }
    }

    private fun KtReferenceExpression.isAssignment() =
        (this as? KtOperationReferenceExpression)?.operationSignTokenType == KtTokens.EQ

    private fun expressionHighlightType(expression: KtCallExpression, callee: KtExpression): HighlightInfoType? {
        with(session) {
            val call = expression.resolveToCall()?.singleCallOrNull<KaCall>() ?: return null
            return getHighlightInfoTypeForCallFromExtension(callee, call)
                ?: getDefaultHighlightInfoTypeForCall(call)
        }
    }

    private fun getDefaultHighlightInfoTypeForCall(call: KaCall): HighlightInfoType? {
        if (call !is KaSimpleFunctionCall) return null
        val type = when (val function = call.symbol) {
            is KaConstructorSymbol -> KotlinHighlightInfoTypeSemanticNames.CONSTRUCTOR_CALL
            is KaAnonymousFunctionSymbol -> null
            is KaNamedFunctionSymbol -> when {
                function.isSuspend -> KotlinHighlightInfoTypeSemanticNames.SUSPEND_FUNCTION_CALL
                call.isImplicitInvoke -> if (function.isBuiltinFunctionInvoke) {
                    KotlinHighlightInfoTypeSemanticNames.VARIABLE_AS_FUNCTION_CALL
                } else {
                    KotlinHighlightInfoTypeSemanticNames.VARIABLE_AS_FUNCTION_LIKE_CALL
                }

                function.callableId == KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID -> KotlinHighlightInfoTypeSemanticNames.KEYWORD
                function.isExtension -> KotlinHighlightInfoTypeSemanticNames.EXTENSION_FUNCTION_CALL
                function.location == KaSymbolLocation.TOP_LEVEL -> KotlinHighlightInfoTypeSemanticNames.PACKAGE_FUNCTION_CALL
                else -> KotlinHighlightInfoTypeSemanticNames.FUNCTION_CALL
            }

            else -> KotlinHighlightInfoTypeSemanticNames.FUNCTION_CALL //TODO ()
        }
        return type
    }

    companion object {
        private val KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID =
            CallableId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("suspend"))

        internal fun KaSession.getHighlightInfoTypeForCallFromExtension(expression: KtExpression, call: KaCall): HighlightInfoType? {
            val session = this
            val highlightInfoType =
                KotlinCallHighlighterExtension.EP_NAME.extensionList.firstNotNullOfOrNull {
                    with(it) {
                        session.highlightCall(expression, call)
                    }
                }
            return highlightInfoType
        }
    }
}