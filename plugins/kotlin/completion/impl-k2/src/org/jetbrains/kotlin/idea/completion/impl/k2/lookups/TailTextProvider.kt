// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups


import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererModifierFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KtTypeParametersRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinCompletionImplK2Bundle
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer.renderFunctionParameters
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance

internal object TailTextProvider {
    fun KtAnalysisSession.getTailText(symbol: KtCallableSymbol, substitutor: KtSubstitutor): String = buildString {
        if (symbol is KtFunctionSymbol) {
            if (insertLambdaBraces(symbol)) {
                append(" {...}")
            } else {
                append(renderFunctionParameters(symbol, substitutor))
            }
        }

        symbol.callableIdIfNonLocal
            ?.takeIf { it.className == null }
            ?.let { callableId ->
                append(" (")
                append(callableId.packageName.asStringForTailText())
                append(")")
            }

        symbol.receiverType?.let { receiverType ->
            val renderedType = receiverType.render(CompletionShortNamesRenderer.renderer, position = Variance.INVARIANT)
            append(KotlinCompletionImplK2Bundle.message("presentation.tail.for.0", renderedType))
        }
    }

    fun KtAnalysisSession.getTailText(
        symbol: KtClassLikeSymbol,
        usePackageFqName: Boolean = false,
        addTypeParameters: Boolean = true
    ): String = buildString {
        symbol.classIdIfNonLocal?.let { classId ->
            if (addTypeParameters && symbol.typeParameters.isNotEmpty()) {
                append(symbol.typeParameters.joinToString(", ", "<", ">") { it.render(typeParameterRenderer) })
            }

            val fqName = if (usePackageFqName) classId.packageFqName else classId.asSingleFqName().parent()

            append(" (")
            append(fqName.asStringForTailText())
            append(")")
        }
    }

    private fun FqName.asStringForTailText(): String =
        if (isRoot) "<root>" else asString()

    fun KtAnalysisSession.insertLambdaBraces(symbol: KtFunctionSymbol): Boolean {
        val singleParam = symbol.valueParameters.singleOrNull()
        return singleParam != null && !singleParam.hasDefaultValue && singleParam.returnType is KtFunctionalType
    }

    fun KtAnalysisSession.insertLambdaBraces(symbol: KtFunctionalType): Boolean {
        val singleParam = symbol.parameterTypes.singleOrNull()
        return singleParam != null && singleParam is KtFunctionalType
    }

    private val typeParameterRenderer = KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
        modifiersRenderer = modifiersRenderer.with { modifierFilter = KtRendererModifierFilter.NONE }
        typeParametersRenderer = KtTypeParametersRenderer.WIHTOUT_BOUNDS
    }
}