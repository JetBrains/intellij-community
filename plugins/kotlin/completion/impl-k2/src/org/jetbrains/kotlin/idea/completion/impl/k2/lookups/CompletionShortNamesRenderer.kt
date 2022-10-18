// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor

internal object CompletionShortNamesRenderer {
    fun KtAnalysisSession.renderFunctionParameters(function: KtFunctionSymbol, substitutor: KtSubstitutor): String {
        return function.valueParameters.joinToString(", ", "(", ")") { renderFunctionParameter(it, substitutor) }
    }

    fun KtAnalysisSession.renderVariable(function: KtVariableLikeSymbol, substitutor: KtSubstitutor): String {
        return renderReceiver(function, substitutor)
    }

    private fun KtAnalysisSession.renderReceiver(symbol: KtCallableSymbol, substitutor: KtSubstitutor): String {
        val receiverType = symbol.receiverType?.let { substitutor.substitute(it) } ?: return ""
        return receiverType.render(TYPE_RENDERING_OPTIONS) + "."
    }

    private fun KtAnalysisSession.renderFunctionParameter(param: KtValueParameterSymbol, substitutor: KtSubstitutor): String =
        "${if (param.isVararg) "vararg " else ""}${param.name.asString()}: ${
            substitutor.substitute(param.returnType).render(TYPE_RENDERING_OPTIONS)
        }"

    val TYPE_RENDERING_OPTIONS = KtTypeRendererOptions.SHORT_NAMES
}