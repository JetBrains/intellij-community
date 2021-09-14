// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol

internal object CompletionShortNamesRenderer {
    fun KtAnalysisSession.renderFunctionParameters(function: KtFunctionSymbol): String {
        val receiver = renderReceiver(function)
        val parameters = function.valueParameters.joinToString(", ", "(", ")") { renderFunctionParameter(it) }
        return receiver + parameters
    }

    fun KtAnalysisSession.renderVariable(function: KtVariableLikeSymbol): String {
        return renderReceiver(function)
    }
    
    private fun KtAnalysisSession.renderReceiver(symbol: KtCallableSymbol): String {
        val receiverType = symbol.receiverType?.type ?: return ""
        return receiverType.render(TYPE_RENDERING_OPTIONS) + "."
    }

    private fun KtAnalysisSession.renderFunctionParameter(param: KtValueParameterSymbol): String =
        "${if (param.isVararg) "vararg " else ""}${param.name.asString()}: ${param.annotatedType.type.render(TYPE_RENDERING_OPTIONS)}"

    val TYPE_RENDERING_OPTIONS = KtTypeRendererOptions.SHORT_NAMES
}