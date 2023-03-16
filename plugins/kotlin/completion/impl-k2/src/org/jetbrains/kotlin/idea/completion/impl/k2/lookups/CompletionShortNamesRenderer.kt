// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.types.Variance

internal object CompletionShortNamesRenderer {
    fun KtAnalysisSession.renderFunctionParameters(function: KtFunctionLikeSignature<*>): String {
        return function.valueParameters.joinToString(", ", "(", ")") { renderFunctionParameter(it) }
    }

    fun KtAnalysisSession.renderVariable(variable: KtVariableLikeSignature<*>): String {
        return renderReceiver(variable)
    }

    private fun KtAnalysisSession.renderReceiver(variable: KtVariableLikeSignature<*>): String {
        val receiverType = variable.receiverType ?: return ""
        return receiverType.render(renderer, position = Variance.INVARIANT) + "."
    }

    private fun KtAnalysisSession.renderFunctionParameter(param: KtVariableLikeSignature<KtValueParameterSymbol>): String =
        "${if (param.symbol.isVararg) "vararg " else ""}${param.name.asString()}: ${
            param.returnType.render(renderer, position = Variance.INVARIANT)
        }"

    val renderer = KtTypeRendererForSource.WITH_SHORT_NAMES
}