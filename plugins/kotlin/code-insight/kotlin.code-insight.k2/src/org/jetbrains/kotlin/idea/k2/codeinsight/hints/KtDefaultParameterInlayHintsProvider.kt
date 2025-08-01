// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.endOffset

/**
 * Provides inlay hints for default parameter values in overridden methods.
 *
 * When a method with default parameters is overridden, this provider shows
 * the default value as an inlay hint in the overriding method.
 */
class KtDefaultParameterInlayHintsProvider : AbstractKtInlayHintsProvider() {
    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        val function = element as? KtFunction ?: return

        if (!function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return

        val parameters = function.valueParameters
        if (parameters.isEmpty()) return

        analyze(function) {
            collectDefaultParameterValues(function, parameters, sink)
        }
    }

    private fun KaSession.collectDefaultParameterValues(
        function: KtFunction,
        parameters: List<KtParameter>,
        sink: InlayTreeSink
    ) {
        val functionSymbol = function.symbol as? KaFunctionSymbol ?: return

        val overriddenFunctions = functionSymbol.allOverriddenSymbols.filterIsInstance<KaFunctionSymbol>()

        for ((index, parameter) in parameters.withIndex()) {
            if (parameter.defaultValue != null) continue

            for (overriddenFunction in overriddenFunctions) {
                val overriddenParameter = overriddenFunction.valueParameters.getOrNull(index) ?: continue
                val defaultValueExpr = overriddenParameter.defaultValue ?: continue
                sink.addPresentation(
                    InlineInlayPosition(parameter.endOffset, false),
                    hintFormat = HintFormat.default
                ) {
                    text(" = ${defaultValueExpr.text}",
                         InlayActionData(
                            PsiPointerInlayActionPayload(pointer = defaultValueExpr.createSmartPointer()),
                         PsiPointerInlayActionNavigationHandler.HANDLER_ID
                    ))
                }
                break
            }
        }
    }
}