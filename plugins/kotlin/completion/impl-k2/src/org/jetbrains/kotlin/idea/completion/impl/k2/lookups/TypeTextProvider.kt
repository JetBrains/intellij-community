// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.lookups

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtSubstitutor
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer
import org.jetbrains.kotlin.types.Variance

internal object TypeTextProvider {
    /**
     * Creates lookup element's type text, based on provided symbol.
     *
     * @param treatAsFunctionCall true if variable with functional type should be treated as function call, in the following example:
     * ```
     * fun Int.test(foo: Int.() -> Unit) {
     *     fo<caret>
     * }
     * ```
     * a lookup element `foo()` is suggested and its type text should be `Unit`.
     */
    fun KtAnalysisSession.getTypeText(symbol: KtSymbol, treatAsFunctionCall: Boolean, substitutor: KtSubstitutor): String? {
        val renderer = CompletionShortNamesRenderer.renderer

        return when (symbol) {
            is KtTypeAliasSymbol -> symbol.expandedType.render(renderer, position = Variance.INVARIANT)
            is KtFunctionLikeSymbol -> substitutor.substitute(symbol.returnType).render(renderer, position = Variance.INVARIANT)

            is KtVariableLikeSymbol -> {
                val symbolType = substitutor.substitute(symbol.returnType)
                if (treatAsFunctionCall && symbolType is KtFunctionalType) {
                    substitutor.substitute(symbolType.returnType).render(renderer, position = Variance.INVARIANT)
                } else {
                    symbolType.render(renderer, position = Variance.INVARIANT)
                }
            }

            else -> null
        }
    }
}