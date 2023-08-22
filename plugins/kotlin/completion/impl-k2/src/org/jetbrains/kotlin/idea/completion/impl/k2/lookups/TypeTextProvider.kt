// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.signatures.KtCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtFunctionLikeSignature
import org.jetbrains.kotlin.analysis.api.signatures.KtVariableLikeSignature
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer
import org.jetbrains.kotlin.idea.completion.lookups.renderNonErrorOrUnsubstituted
import org.jetbrains.kotlin.types.Variance

internal object TypeTextProvider {
    /**
     * Creates lookup element's type text, based on provided classifier symbol.
     */
    context(KtAnalysisSession)
fun getTypeTextForClassifier(symbol: KtClassifierSymbol): String? = when (symbol) {
        is KtTypeAliasSymbol -> symbol.expandedType.render(renderer, position = Variance.INVARIANT)
        else -> null
    }

    /**
     * Creates lookup element's type text, based on provided callable signature.
     *
     * @param treatAsFunctionCall true if variable with functional type should be treated as function call, in the following example:
     * ```
     * fun Int.test(foo: Int.() -> Unit) {
     *     fo<caret>
     * }
     * ```
     * a lookup element `foo()` is suggested and its type text should be `Unit`.
     */
    context(KtAnalysisSession)
fun getTypeTextForCallable(
        signature: KtCallableSignature<*>,
        treatAsFunctionCall: Boolean
    ): String? = when (signature) {
        is KtFunctionLikeSignature<*> -> signature.returnType.renderNonErrorOrUnsubstituted(signature.symbol.returnType)

        is KtVariableLikeSignature<*> -> {
            val type = signature.returnType
            val typeToRender = when {
                treatAsFunctionCall && type is KtFunctionalType -> type.returnType
                else -> type
            }

            typeToRender.render(renderer, position = Variance.INVARIANT)
        }
    }

    private val renderer = CompletionShortNamesRenderer.rendererVerbose
}