// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors as Colors

internal class FunctionCallHighlighter(
    holder: AnnotationHolder,
    project: Project
) : AfterResolveHighlighter(holder, project) {

    context(KtAnalysisSession)
    override fun highlight(element: KtElement) {
        when (element) {
            is KtBinaryExpression -> highlightBinaryExpression(element)
            is KtCallExpression -> highlightCallExpression(element)
            else -> {}
        }
    }

    context(KtAnalysisSession)
    private fun highlightBinaryExpression(expression: KtBinaryExpression) {
        val operationReference = expression.operationReference as? KtReferenceExpression ?: return
        if (operationReference.isAssignment()) return
        val call = expression.resolveCall()?.successfulCallOrNull<KtCall>() ?: return
        if (call is KtSimpleFunctionCall && (call.symbol as? KtFunctionSymbol)?.isOperator == true) return
        getTextAttributesForCall(call)?.let { attributes ->
            highlightName(operationReference, attributes)
        }
    }

    private fun KtReferenceExpression.isAssignment() =
        (this as? KtOperationReferenceExpression)?.operationSignTokenType == KtTokens.EQ

    context(KtAnalysisSession)
    private fun highlightCallExpression(expression: KtCallExpression) {
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
    }

    context(KtAnalysisSession)
    private fun getTextAttributesForCall(call: KtCall): TextAttributesKey? {
        if (call !is KtSimpleFunctionCall) return null
        return when (val function = call.symbol) {
            is KtConstructorSymbol -> Colors.CONSTRUCTOR_CALL
            is KtAnonymousFunctionSymbol -> null
            is KtFunctionSymbol -> when {
                function.isSuspend -> Colors.SUSPEND_FUNCTION_CALL
                call.isImplicitInvoke -> if (function.isBuiltinFunctionInvoke) {
                    Colors.VARIABLE_AS_FUNCTION_CALL
                } else {
                    Colors.VARIABLE_AS_FUNCTION_LIKE_CALL
                }

                function.callableIdIfNonLocal == KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID -> Colors.KEYWORD
                function.isExtension -> Colors.EXTENSION_FUNCTION_CALL
                function.symbolKind == KtSymbolKind.TOP_LEVEL -> Colors.PACKAGE_FUNCTION_CALL
                else -> Colors.FUNCTION_CALL
            }

            else -> Colors.FUNCTION_CALL //TODO ()
        }
    }

    companion object {
        private val KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID =
            CallableId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("suspend"))
    }
}