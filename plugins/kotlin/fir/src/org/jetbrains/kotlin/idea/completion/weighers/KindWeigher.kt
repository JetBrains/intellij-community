/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.completion.KeywordLookupObject
import org.jetbrains.kotlin.idea.completion.contributors.keywords.ReturnKeywordHandler.isReturnAtHighlyLikelyPosition
import org.jetbrains.kotlin.idea.completion.lookups.KotlinCallableLookupObject
import org.jetbrains.kotlin.idea.completion.lookups.factories.PackagePartLookupObject
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtSymbol
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

internal object KindWeigher {
    private enum class Weight {
        RETURN_KEYWORD_AT_HIGHLY_LIKELY_POSITION,
        ENUM_MEMBER,
        CALLABLE,
        KEYWORD,
        DEFAULT,
        PACKAGES
    }

    const val WEIGHER_ID = "kotlin.kind"

    private var LookupElement.isEnumEntry: Boolean by NotNullableUserDataProperty(Key("KOTLIN_KIND_WEIGHER_IS_ENUM"), false)

    fun KtAnalysisSession.addWeight(lookupElement: LookupElement, symbol: KtSymbol) {
        lookupElement.isEnumEntry = symbol is KtEnumEntrySymbol
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<Nothing> {
            if (element.isEnumEntry) return Weight.ENUM_MEMBER
            if (element.isReturnAtHighlyLikelyPosition) return Weight.RETURN_KEYWORD_AT_HIGHLY_LIKELY_POSITION
            return when (element.`object`) {
                is KeywordLookupObject -> Weight.KEYWORD
                is PackagePartLookupObject -> Weight.PACKAGES
                is KotlinCallableLookupObject -> Weight.CALLABLE
                else -> Weight.DEFAULT
            }
        }
    }
}