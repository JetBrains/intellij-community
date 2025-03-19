// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

/**
 * This weigher checks whether the callable should be prioritized because it is suitable based on the context. In the following code:
 * ```
 * class B {
 *     open fun foo1() {}
 *     open fun foo2() {}
 * }
 *
 * class C : B() {
 *     override fun foo2() {
 *         super.fo<caret>
 *     }
 * }
 * ```
 * `foo2` should be prioritized.
 */
internal object PreferContextualCallablesWeigher {
    const val WEIGHER_ID = "kotlin.preferContextualCallables"

    private var LookupElement.isContextualCallable: Boolean
            by NotNullableUserDataProperty(Key("KOTLIN_PREFER_CONTEXTUAL_CALLABLES_WEIGHER"), false)

    /**
     * Marks [symbol] as contextual if [symbol] or one of its overridden symbols is equal to or overridden by
     * one of the callables containing current position.
     */
    context(KaSession)
    fun addWeight(lookupElement: LookupElement, symbol: KaCallableSymbol, contextualSymbolsCache: WeighingContext.ContextualSymbolsCache) {
        if (symbol !is KaNamedSymbol || symbol.name !in contextualSymbolsCache) return

        val symbolsToCheck = symbol.allOverriddenSymbolsWithSelf

        lookupElement.isContextualCallable = symbolsToCheck.any { contextualSymbolsCache.symbolIsPresentInContext(it) }
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Boolean = !element.isContextualCallable
    }
}