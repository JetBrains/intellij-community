// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.completion.KeywordLookupObject
import org.jetbrains.kotlin.idea.completion.contributors.keywords.ReturnKeywordHandler.isReturnAtHighlyLikelyPosition
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.KotlinCallableLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.PackagePartLookupObject
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object KindWeigher {
    private enum class Weight {
        RETURN_KEYWORD_AT_HIGHLY_LIKELY_POSITION,
        NULL_KEYWORD_AT_HIGHLY_LIKELY_POSITION,
        ENUM_MEMBER,
        CALLABLE,
        KEYWORD,
        NAMED_ARGUMENT,
        DEFAULT,
        PACKAGES,
        SYMBOL_TO_SKIP,
    }

    const val WEIGHER_ID = "kotlin.kind"

    private var LookupElement.isConstructorCall: Boolean by NotNullableUserDataProperty(Key("KOTLIN_KIND_WEIGHER_IS_CONSTRUCTOR_CALL"), false)

    private var LookupElement.isEnumEntry: Boolean by NotNullableUserDataProperty(Key("KOTLIN_KIND_WEIGHER_IS_ENUM"), false)

    private var LookupElement.isNullAtHighlyLikelyPosition: Boolean by NotNullableUserDataProperty(
        Key("KOTLIN_IS_NULL_AT_HIGHLY_LIKELY_POSITION"),
        defaultValue = false
    )

    private var LookupElement.isSymbolToSkip: Boolean by NotNullableUserDataProperty(Key("KOTLIN_KIND_WEIGHER_IS_SYMBOL_TO_SKIP"), false)

    context(KaSession)
    fun addWeight(lookupElement: LookupElement, symbol: KaSymbol?, context: WeighingContext) {
        lookupElement.isSymbolToSkip = symbol in context.symbolsToSkip
        lookupElement.isEnumEntry = symbol is KaEnumEntrySymbol
        lookupElement.isConstructorCall = symbol is KaNamedClassSymbol && lookupElement.`object` is FunctionCallLookupObject

        if (lookupElement.lookupString != KtTokens.NULL_KEYWORD.value || lookupElement.`object` !is KeywordLookupObject) return
        lookupElement.isNullAtHighlyLikelyPosition = context.isPositionSuitableForNull && context.expectedType == null
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<Nothing> {
            if (element.isSymbolToSkip) return Weight.SYMBOL_TO_SKIP
            if (element.isEnumEntry) return Weight.ENUM_MEMBER
            if (element.isReturnAtHighlyLikelyPosition) return Weight.RETURN_KEYWORD_AT_HIGHLY_LIKELY_POSITION
            if (element.isNullAtHighlyLikelyPosition) return Weight.NULL_KEYWORD_AT_HIGHLY_LIKELY_POSITION
            if (element.isConstructorCall) return Weight.DEFAULT
            return when (element.`object`) {
                is KeywordLookupObject -> Weight.KEYWORD
                is PackagePartLookupObject -> Weight.PACKAGES
                is KotlinCallableLookupObject -> Weight.CALLABLE
                is NamedArgumentLookupObject -> Weight.NAMED_ARGUMENT
                else -> Weight.DEFAULT
            }
        }
    }
}