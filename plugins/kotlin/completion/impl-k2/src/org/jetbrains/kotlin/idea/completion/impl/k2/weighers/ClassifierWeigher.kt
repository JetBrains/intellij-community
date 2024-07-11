// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.psi.UserDataProperty

internal object ClassifierWeigher {
    const val WEIGHER_ID = "kotlin.classifierWeigher"
    const val LOW_PRIORITY = Int.MAX_VALUE

    private enum class Weight {
        LOCAL,
        NON_LOCAL
    }

    context(KaSession)
fun addWeight(lookupElement: LookupElement, symbol: KaSymbol, symbolOrigin: CompletionSymbolOrigin) {
        if (symbol !is KaClassifierSymbol) return

        val isLocal = (symbol as? KaClassLikeSymbol)?.location == KaSymbolLocation.LOCAL
        val weight = if (isLocal) Weight.LOCAL else Weight.NON_LOCAL

        val priority = when (symbolOrigin) {
            is CompletionSymbolOrigin.Scope -> symbolOrigin.kind.indexInTower
            is CompletionSymbolOrigin.Index -> LOW_PRIORITY
        }
        lookupElement.classifierWeight = CompoundWeight2(weight, priority)
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*> =
            element.classifierWeight ?: CompoundWeight2(Weight.NON_LOCAL, LOW_PRIORITY)
    }

    private var LookupElement.classifierWeight: CompoundWeight2<Weight, Int>? by UserDataProperty(
        Key<CompoundWeight2<Weight, Int>>("KOTLIN_CLASSIFIER_WEIGHT")
    )
}