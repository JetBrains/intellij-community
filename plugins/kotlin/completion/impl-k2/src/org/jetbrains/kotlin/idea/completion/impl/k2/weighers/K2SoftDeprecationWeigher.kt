// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.completion.implCommon.weighers.SoftDeprecationWeigher
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object K2SoftDeprecationWeigher {
    private var LookupElement.isSoftDeprecated: Boolean
            by NotNullableUserDataProperty(Key("KOTLIN_SOFT_DEPRECATED"), false)

    fun KtAnalysisSession.addWeight(
        lookupElement: LookupElement,
        symbol: KtSymbol,
        languageVersionSettings: LanguageVersionSettings
    ) {
        val fqName = (symbol as? KtCallableSymbol)?.callableIdIfNonLocal?.asSingleFqName()
        lookupElement.isSoftDeprecated = fqName != null &&
                SoftDeprecationWeigher.isSoftDeprecatedFqName(fqName, languageVersionSettings)
    }

    object Weigher : LookupElementWeigher(SoftDeprecationWeigher.WEIGHER_ID) {
        override fun weigh(element: LookupElement, context: WeighingContext): Boolean {
            return element.isSoftDeprecated
        }
    }
}