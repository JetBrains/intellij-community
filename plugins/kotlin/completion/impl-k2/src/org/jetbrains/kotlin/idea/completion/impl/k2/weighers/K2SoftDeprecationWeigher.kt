// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.completion.implCommon.weighers.EnumValuesSoftDeprecationWeigher
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
        val callableSymbol = symbol as? KtCallableSymbol ?: return
        lookupElement.isSoftDeprecated = isLibrarySoftDeprecatedMethod(callableSymbol, languageVersionSettings) ||
                isEnumValuesSoftDeprecatedMethod(callableSymbol, languageVersionSettings)
    }

    private fun isLibrarySoftDeprecatedMethod(symbol: KtCallableSymbol, languageVersionSettings: LanguageVersionSettings): Boolean {
        val fqName = symbol.callableIdIfNonLocal?.asSingleFqName()
        return fqName != null &&
                SoftDeprecationWeigher.isSoftDeprecatedFqName(fqName, languageVersionSettings)
    }

    private fun KtAnalysisSession.isEnumValuesSoftDeprecatedMethod(
        symbol: KtCallableSymbol,
        languageVersionSettings: LanguageVersionSettings
    ): Boolean {
        return EnumValuesSoftDeprecationWeigher.weigherIsEnabled(languageVersionSettings) &&
                KtClassKind.ENUM_CLASS == (symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind &&
                EnumValuesSoftDeprecationWeigher.VALUES_METHOD_NAME == symbol.callableIdIfNonLocal?.callableName &&
                // Don't touch user-declared methods with the name "values"
                symbol.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED
    }

    object Weigher : LookupElementWeigher(SoftDeprecationWeigher.WEIGHER_ID) {
        override fun weigh(element: LookupElement, context: WeighingContext): Boolean {
            return element.isSoftDeprecated
        }
    }
}