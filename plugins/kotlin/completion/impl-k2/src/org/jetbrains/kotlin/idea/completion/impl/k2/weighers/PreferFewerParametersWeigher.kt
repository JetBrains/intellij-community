// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object PreferFewerParametersWeigher {
    const val WEIGHER_ID = "kotlin.preferFewerParameters"

    private var LookupElement.parametersCount: Int
            by NotNullableUserDataProperty(Key("KOTLIN_PREFER_FEWER_PARAMETERS_WEIGHER"), 0)

    context(KtAnalysisSession)
    fun addWeight(lookupElement: LookupElement, symbol: KtCallableSymbol) {
        lookupElement.parametersCount = (symbol as? KtFunctionLikeSymbol)?.valueParameters?.size ?: 0
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Int = element.parametersCount
    }
}