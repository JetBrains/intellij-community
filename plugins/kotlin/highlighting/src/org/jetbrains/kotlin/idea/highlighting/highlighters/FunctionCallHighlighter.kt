// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.highlighting.HighlightingFactory
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.highlighting.KotlinCallHighlighterExtension
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class FunctionCallHighlighter(
  project: Project
) : AfterResolveHighlighter(project) {

    context(KtAnalysisSession)
    override fun highlight(element: KtElement): List<HighlightInfo.Builder> {
        return when (element) {
            is KtBinaryExpression -> highlightBinaryExpression(element)
            is KtCallExpression -> highlightCallExpression(element)
            else -> emptyList()
        }
    }

    context(KtAnalysisSession)
    private fun highlightBinaryExpression(expression: KtBinaryExpression): List<HighlightInfo.Builder> {
        val operationReference = expression.operationReference as? KtReferenceExpression ?: return emptyList()
        if (operationReference.isAssignment()) return emptyList()
        val call = expression.resolveCall()?.successfulCallOrNull<KtCall>() ?: return emptyList()
        if (call is KtSimpleFunctionCall && (call.symbol as? KtFunctionSymbol)?.isOperator == true) return emptyList()
        val h = getDefaultHighlightInfoTypeForCall(call)?.let { highlightInfoType ->
            HighlightingFactory.highlightName(operationReference, highlightInfoType)
        }
        return listOfNotNull(h)
    }

    private fun KtReferenceExpression.isAssignment() =
        (this as? KtOperationReferenceExpression)?.operationSignTokenType == KtTokens.EQ

    context(KtAnalysisSession)
    private fun highlightCallExpression(expression: KtCallExpression): List<HighlightInfo.Builder> {
        val callee = expression.calleeExpression ?: return emptyList()
        val call = expression.resolveCall()?.singleCallOrNull<KtCall>() ?: return emptyList()
        if (callee is KtLambdaExpression || callee is KtCallExpression /* KT-16159 */) return emptyList()
        val highlightInfoType = getHighlightInfoTypeForCallFromExtension(callee, call)
            ?: getDefaultHighlightInfoTypeForCall(call)
            ?: return emptyList()
        return listOfNotNull(HighlightingFactory.highlightName(callee, highlightInfoType))
    }

    context(KtAnalysisSession)
    private fun getHighlightInfoTypeForCallFromExtension(callee: KtExpression, call: KtCall): HighlightInfoType? =
        KotlinCallHighlighterExtension.EP_NAME.getExtensions(project).firstNotNullOfOrNull { it.highlightCall(callee, call) }

    context(KtAnalysisSession)
    private fun getDefaultHighlightInfoTypeForCall(call: KtCall): HighlightInfoType? {
        if (call !is KtSimpleFunctionCall) return null
        return when (val function = call.symbol) {
            is KtConstructorSymbol -> KotlinHighlightInfoTypeSemanticNames.CONSTRUCTOR_CALL
            is KtAnonymousFunctionSymbol -> null
            is KtFunctionSymbol -> when {
                function.isSuspend -> KotlinHighlightInfoTypeSemanticNames.SUSPEND_FUNCTION_CALL
                call.isImplicitInvoke -> if (function.isBuiltinFunctionInvoke) {
                    KotlinHighlightInfoTypeSemanticNames.VARIABLE_AS_FUNCTION_CALL
                } else {
                    KotlinHighlightInfoTypeSemanticNames.VARIABLE_AS_FUNCTION_LIKE_CALL
                }

                function.callableIdIfNonLocal == KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID -> KotlinHighlightInfoTypeSemanticNames.KEYWORD
                function.isExtension -> KotlinHighlightInfoTypeSemanticNames.EXTENSION_FUNCTION_CALL
                function.symbolKind == KtSymbolKind.TOP_LEVEL -> KotlinHighlightInfoTypeSemanticNames.PACKAGE_FUNCTION_CALL
                else -> KotlinHighlightInfoTypeSemanticNames.FUNCTION_CALL
            }

            else -> KotlinHighlightInfoTypeSemanticNames.FUNCTION_CALL //TODO ()
        }
    }

    companion object {
        private val KOTLIN_SUSPEND_BUILT_IN_FUNCTION_FQ_NAME_CALLABLE_ID =
            CallableId(StandardNames.BUILT_INS_PACKAGE_FQ_NAME, Name.identifier("suspend"))
    }
}
