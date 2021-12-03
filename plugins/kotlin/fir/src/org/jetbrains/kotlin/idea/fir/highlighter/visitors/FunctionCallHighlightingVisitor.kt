// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter.visitors

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.serialization.deserialization.KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class FunctionCallHighlightingVisitor(
    analysisSession: KtAnalysisSession,
    holder: AnnotationHolder
) : FirAfterResolveHighlightingVisitor(analysisSession, holder) {
    override fun visitBinaryExpression(expression: KtBinaryExpression) = with(analysisSession) {
        val operationReference = expression.operationReference as? KtReferenceExpression ?: return
        if (operationReference.isAssignment()) return
        val call = expression.resolveCall()?.successfulCallOrNull<KtCall>() ?: return
        if (call is KtSimpleFunctionCall && (call.symbol as? KtFunctionSymbol)?.isOperator == true) return
        getTextAttributesForCall(call)?.let { attributes ->
            highlightName(operationReference, attributes)
        }
        super.visitBinaryExpression(expression)
    }

    private fun KtReferenceExpression.isAssignment() =
        (this as? KtOperationReferenceExpression)?.operationSignTokenType == KtTokens.EQ

    override fun visitCallExpression(expression: KtCallExpression) = with(analysisSession) {
        expression.calleeExpression
            ?.takeUnless { it is KtLambdaExpression }
            ?.takeUnless { it is KtCallExpression /* KT-16159 */ }
            ?.let { callee ->
                expression.resolveCall().singleCallOrNull<KtCall>()?.let { call ->
                    getTextAttributesForCall(call)?.let { attributes ->
                        highlightName(callee, attributes)
                    }
                }
            }
        super.visitCallExpression(expression)
    }

    private fun getTextAttributesForCall(call: KtCall): TextAttributesKey? {
        if (call !is KtSimpleFunctionCall) return null
        return when (val function = call.symbol) {
            is KtConstructorSymbol -> Colors.CONSTRUCTOR_CALL
            is KtAnonymousFunctionSymbol -> null
            is KtFunctionSymbol -> when {
                call.isImplicitInvoke -> if (function.isBuiltinFunctionInvoke) {
                    Colors.VARIABLE_AS_FUNCTION_CALL
                } else {
                    Colors.VARIABLE_AS_FUNCTION_LIKE_CALL
                }
                function.isSuspend -> Colors.SUSPEND_FUNCTION_CALL
                function.callableIdIfNonLocal == KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID -> Colors.KEYWORD
                function.isExtension -> Colors.EXTENSION_FUNCTION_CALL
                function.symbolKind == KtSymbolKind.TOP_LEVEL -> Colors.PACKAGE_FUNCTION_CALL
                else -> Colors.FUNCTION_CALL
            }
            else -> Colors.FUNCTION_CALL //TODO ()
        }
    }
}