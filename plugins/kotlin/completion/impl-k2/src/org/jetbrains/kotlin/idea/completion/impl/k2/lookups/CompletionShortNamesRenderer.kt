// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.KaExpandedTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.types.Variance

internal object CompletionShortNamesRenderer {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun renderFunctionalTypeParameters(functionalType: KaFunctionType): String =
        functionalType.parameterTypes.joinToString(separator = ", ", prefix = "(", postfix = ")") {
            it.render(rendererVerbose, position = Variance.INVARIANT)
        }

    context(KaSession)
    fun renderVariable(variable: KaVariableSignature<*>): String {
        return renderReceiver(variable)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun renderReceiver(variable: KaVariableSignature<*>): String {
        val receiverType = variable.receiverType ?: return ""
        return receiverType.render(rendererVerbose, position = Variance.INVARIANT) + "."
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun renderFunctionParameter(parameter: KaVariableSignature<KaValueParameterSymbol>): String =
        "${if (parameter.symbol.isVararg) "vararg " else ""}${parameter.name.asString()}: ${
            parameter.returnType.renderNonErrorOrUnsubstituted(parameter.symbol.returnType)
        }${if (parameter.symbol.hasDefaultValue) " = ..." else ""}"

    @KaExperimentalApi
    val renderer = KaTypeRendererForSource.WITH_SHORT_NAMES

    @KaExperimentalApi
    val rendererVerbose = renderer.with {
        expandedTypeRenderingMode = KaExpandedTypeRenderingMode.RENDER_ABBREVIATED_TYPE_WITH_EXPANDED_TYPE_COMMENT
    }
}

context(KaSession)
@KaExperimentalApi
internal fun KaType.renderNonErrorOrUnsubstituted(
    unsubstituted: KaType,
    renderer: KaTypeRenderer = CompletionShortNamesRenderer.rendererVerbose
): String {
    val typeToRender = this.takeUnless { it is KaErrorType } ?: unsubstituted
    return typeToRender.render(renderer, position = Variance.INVARIANT)
}