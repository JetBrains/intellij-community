// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.KaExpandedTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFunctionalTypeRenderer
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance

internal object CompletionShortNamesRenderer {

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun renderFunctionalTypeParameters(functionalType: KaFunctionType): String = functionalType.parameterTypes.joinToString(
        prefix = "(",
        postfix = ")",
    ) { it.renderVerbose() }

    context(KaSession)
    fun renderVariable(variable: KaVariableSignature<*>): String {
        return renderReceiver(variable)
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun renderReceiver(variable: KaVariableSignature<*>): String {
        val receiverType = variable.receiverType ?: return ""
        return receiverType.renderVerbose() + "."
    }

    context(KaSession)
    fun renderFunctionParameters(
        parameters: List<KaVariableSignature<KaValueParameterSymbol>>,
    ): @NonNls String = parameters.joinToString(
        prefix = "(",
        postfix = ")",
    ) { renderFunctionParameter(it) }

    context(KaSession)
    fun renderTrailingFunction(
        trailingFunctionSignature: KaVariableSignature<KaValueParameterSymbol>,
        trailingFunctionType: KaFunctionType,
    ): @NonNls String = buildString {
        append(" { ")
        appendParameter(
            parameterName = trailingFunctionSignature.name,
            parameterType = trailingFunctionType,
        )
        append(" }")
    }

    context(KaSession)
    private fun renderFunctionParameter(
        parameter: KaVariableSignature<KaValueParameterSymbol>,
    ): @NonNls String = buildString {
        val symbol = parameter.symbol

        if (symbol.isVararg) {
            append("vararg ")
        }
        appendParameter(
            parameterName = parameter.name,
            parameterType = parameter.returnType.takeUnless { it is KaErrorType } ?: symbol.returnType,
        )

        if (symbol.hasDefaultValue) {
            append(" = ...")
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun <A : Appendable> A.appendParameter(
        parameterName: Name,
        parameterType: KaType,
    ): A = apply {
        append(parameterName.render())
        append(": ")
        append(parameterType.renderVerbose())
    }

    @KaExperimentalApi
    val renderer = KaTypeRendererForSource.WITH_SHORT_NAMES_WITHOUT_PARAMETER_NAMES

    @KaExperimentalApi
    val rendererVerbose = renderer.with {
        expandedTypeRenderingMode = KaExpandedTypeRenderingMode.RENDER_ABBREVIATED_TYPE_WITH_EXPANDED_TYPE_COMMENT
    }
}

context(KaSession)
@KaExperimentalApi
internal fun KaType.renderVerbose(): @NonNls String = render(
    renderer = CompletionShortNamesRenderer.rendererVerbose,
    position = Variance.INVARIANT,
)