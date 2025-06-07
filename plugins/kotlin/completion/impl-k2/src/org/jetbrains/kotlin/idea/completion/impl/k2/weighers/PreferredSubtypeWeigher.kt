// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.psi.UserDataProperty

internal object PreferredSubtypeWeigher {
    private const val WEIGHER_ID = "kotlin.preferredSubtype"

    private enum class Weight {
        PREFERRED_SUBTYPE,
        PREFERRED_EXACT_TYPE, // In this weigher we prefer subtypes over the exact type
        UNRELATED
    }

    context(KaSession)
    fun addWeight(context: WeighingContext, lookupElement: LookupElement, symbol: KaSymbol) {
        val actualClassType = (symbol as? KaClassLikeSymbol)?.defaultType ?: return
        val preferredSubtype = context.preferredSubtype ?: return
        lookupElement.hasPreferredSubtype = if (actualClassType.semanticallyEquals(preferredSubtype)) {
            Weight.PREFERRED_EXACT_TYPE
        } else if (!actualClassType.isNothingType && actualClassType.isSubtypeOf(preferredSubtype)) {
            Weight.PREFERRED_SUBTYPE
        } else {
            Weight.UNRELATED
        }
    }

    private var LookupElement.hasPreferredSubtype: Weight? by UserDataProperty(Key("KOTLIN_HAS_PREFERRED_SUBTYPE"))

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*> = element.hasPreferredSubtype ?: Weight.UNRELATED
    }
}