// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.types.Variance

internal object CompletionShortNamesRenderer {
    fun KtAnalysisSession.renderFunctionParameters(function: KtFunctionSymbol, substitutor: KtSubstitutor): String {
        return function.valueParameters.joinToString(", ", "(", ")") { renderFunctionParameter(it, substitutor) }
    }

    fun KtAnalysisSession.renderVariable(function: KtVariableLikeSymbol, substitutor: KtSubstitutor): String {
        return renderReceiver(function, substitutor)
    }

    private fun KtAnalysisSession.renderReceiver(symbol: KtCallableSymbol, substitutor: KtSubstitutor): String {
        val receiverType = symbol.receiverType?.let { substitutor.substitute(it) } ?: return ""
        return receiverType.render(renderer, position = Variance.INVARIANT) + "."
    }

    private fun KtAnalysisSession.renderFunctionParameter(param: KtValueParameterSymbol, substitutor: KtSubstitutor): String =
        "${if (param.isVararg) "vararg " else ""}${param.name.asString()}: ${
            substitutor.substitute(param.returnType).render(renderer, position = Variance.INVARIANT)
        }"

    val renderer = KtTypeRendererForSource.WITH_SHORT_NAMES
}