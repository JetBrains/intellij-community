// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups


import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinCompletionImplK2Bundle
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer.renderFunctionParameters
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.Variance

internal object TailTextProvider {
    fun KtAnalysisSession.getTailText(signature: KtCallableSignature<*>): String = buildString {
        if (signature is KtFunctionLikeSignature<*>) {
            if (insertLambdaBraces(signature)) {
                append(" {...}")
            } else {
                append(renderFunctionParameters(signature))
            }
        }

        // use unsubstituted type when rendering receiver type of extension
        signature.symbol.receiverType?.let { receiverType ->
            val renderedType = receiverType.render(CompletionShortNamesRenderer.rendererVerbose, position = Variance.INVARIANT)
            append(KotlinCompletionImplK2Bundle.message("presentation.tail.for.0", renderedType))
        }

        signature.symbol.getContainerPresentation()?.let { append(it) }
    }

    fun KtAnalysisSession.getTailText(
        symbol: KtClassLikeSymbol,
        usePackageFqName: Boolean = false,
        addTypeParameters: Boolean = true
    ): String = buildString {
        symbol.classIdIfNonLocal?.let { classId ->
            if (addTypeParameters && symbol.typeParameters.isNotEmpty()) {
                // We want to render type parameter names without modifiers and bounds, so no renderer is required.
                append(symbol.typeParameters.joinToString(", ", "<", ">") { it.name.render() })
            }

            val fqName = if (usePackageFqName) classId.packageFqName else classId.asSingleFqName().parent()

            append(" (")
            append(fqName.asStringForTailText())
            append(")")
        }
    }

    context(KtAnalysisSession)
    private fun KtCallableSymbol.getContainerPresentation(): String? {
        val callableId = callableIdIfNonLocal ?: return null
        val className = callableId.className

        val packagePresentation = callableId.packageName.asStringForTailText()
        return when {
            !isExtension && className != null -> null
            !isExtension -> " ($packagePresentation)"

            else -> {
                val containerPresentation = className?.asString() ?: packagePresentation
                KotlinCompletionImplK2Bundle.message("presentation.tail.in.0", containerPresentation)
            }
        }
    }

    private fun FqName.asStringForTailText(): String =
        if (isRoot) "<root>" else asString()

    fun KtAnalysisSession.insertLambdaBraces(symbol: KtFunctionLikeSignature<*>): Boolean {
        val singleParam = symbol.valueParameters.singleOrNull()
        return singleParam != null && !singleParam.symbol.hasDefaultValue && singleParam.returnType is KtFunctionalType
    }

    fun KtAnalysisSession.insertLambdaBraces(symbol: KtFunctionalType): Boolean {
        val singleParam = symbol.parameterTypes.singleOrNull()
        return singleParam != null && singleParam is KtFunctionalType
    }
}