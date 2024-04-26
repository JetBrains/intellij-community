// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.types.KtTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KtUsualClassTypeRenderer
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.types.Variance

internal object CompletionShortNamesRenderer {
    context(KtAnalysisSession)
    fun renderFunctionParameters(function: KtFunctionLikeSignature<*>): String {
        return function.valueParameters.joinToString(", ", "(", ")") { renderFunctionParameter(it) }
    }

    context(KtAnalysisSession)
    fun renderFunctionalTypeParameters(functionalType: KtFunctionalType): String =
        functionalType.parameterTypes.joinToString(separator = ", ", prefix = "(", postfix = ")") {
            it.render(rendererVerbose, position = Variance.INVARIANT)
        }

    context(KtAnalysisSession)
    fun renderVariable(variable: KtVariableLikeSignature<*>): String {
        return renderReceiver(variable)
    }

    context(KtAnalysisSession)
    private fun renderReceiver(variable: KtVariableLikeSignature<*>): String {
        val receiverType = variable.receiverType ?: return ""
        return receiverType.render(rendererVerbose, position = Variance.INVARIANT) + "."
    }

    context(KtAnalysisSession)
    private fun renderFunctionParameter(parameter: KtVariableLikeSignature<KtValueParameterSymbol>): String =
        "${if (parameter.symbol.isVararg) "vararg " else ""}${parameter.name.asString()}: ${
            parameter.returnType.renderNonErrorOrUnsubstituted(parameter.symbol.returnType)
        }${if (parameter.symbol.hasDefaultValue) " = ..." else ""}"

    val renderer = KtTypeRendererForSource.WITH_SHORT_NAMES
    val rendererVerbose = renderer.with {
        usualClassTypeRenderer = KtUsualClassTypeRenderer.AS_CLASS_TYPE_WITH_TYPE_ARGUMENTS_VERBOSE
    }
}

context(KtAnalysisSession)
internal fun KtType.renderNonErrorOrUnsubstituted(
    unsubstituted: KtType,
    renderer: KtTypeRenderer = CompletionShortNamesRenderer.rendererVerbose
): String {
    val typeToRender = this.takeUnless { it is KtErrorType } ?: unsubstituted
    return typeToRender.render(renderer, position = Variance.INVARIANT)
}