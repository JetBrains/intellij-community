// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.psi.UserDataProperty

internal object ClassifierWeigher: KotlinLookupElementWeigher("kotlin.classifierWeigher"), KotlinSectionContextWeigher {
    private const val LOW_PRIORITY = Int.MAX_VALUE

    private enum class Weight {
        LOCAL, // for local symbols
        TOP_LEVEL, // for symbols defined on the top level of the file
        NESTED,
    }

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    override fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>? ) {
        val symbol = symbolWithOrigin?.symbol ?: return
        if (symbol !is KaClassifierSymbol) return

        val location = (symbol as? KaClassLikeSymbol)?.location

        val weight1 = when (location) {
            KaSymbolLocation.LOCAL -> Weight.LOCAL
            KaSymbolLocation.CLASS -> Weight.NESTED
            else -> Weight.TOP_LEVEL
        }
        val weight2 = symbolWithOrigin.scopeKind?.indexInTower ?: LOW_PRIORITY

        lookupElement.classifierWeight = CompoundWeight2(weight1, weight2)
    }

    override fun weigh(element: LookupElement): Comparable<*> =
        element.classifierWeight ?: CompoundWeight2(Weight.TOP_LEVEL, LOW_PRIORITY)

    private var LookupElement.classifierWeight: CompoundWeight2<Weight, Int>? by UserDataProperty(
        Key<CompoundWeight2<Weight, Int>>("KOTLIN_CLASSIFIER_WEIGHT")
    )
}