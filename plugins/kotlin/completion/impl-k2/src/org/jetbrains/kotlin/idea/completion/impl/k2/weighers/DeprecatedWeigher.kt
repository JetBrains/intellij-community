// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.deprecationStatus
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object DeprecatedWeigher {
    const val WEIGHER_ID = "kotlin.deprecated"
    private var LookupElement.isDeprecated: Boolean by NotNullableUserDataProperty(Key("KOTLIN_DEPRECATED"), false)

    context(_: KaSession)
    @OptIn(KaExperimentalApi::class)
    fun addWeight(lookupElement: LookupElement, symbol: KaSymbol) {
        lookupElement.isDeprecated = symbol.deprecationStatus != null
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Boolean = element.isDeprecated
    }
}