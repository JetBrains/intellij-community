/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtSymbol
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object DeprecatedWeigher {
    const val WEIGHER_ID = "kotlin.deprecated"
    private var LookupElement.isDeprecated: Boolean by NotNullableUserDataProperty(Key("KOTLIN_DEPRECATED"), false)

    fun KtAnalysisSession.addWeight(lookupElement: LookupElement, symbol: KtSymbol) {
        lookupElement.isDeprecated = symbol.deprecationStatus != null
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Boolean = element.isDeprecated
    }
}