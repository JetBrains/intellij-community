// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.psi.UserDataProperty


internal object ExpectedTypeWeigher {
    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): MatchesExpectedType =
            element.matchesExpectedType ?: MatchesExpectedType.NON_TYPABLE
    }

    fun KtAnalysisSession.addWeight(context: WeighingContext, lookupElement: LookupElement, symbol: KtSymbol) {
        lookupElement.matchesExpectedType = matchesExpectedType(symbol, context.expectedType)
    }

    private fun KtAnalysisSession.matchesExpectedType(
        symbol: KtSymbol,
        expectedType: KtType?
    ) = when {
        expectedType == null -> MatchesExpectedType.NON_TYPABLE
        symbol !is KtCallableSymbol -> MatchesExpectedType.NON_TYPABLE
        else -> MatchesExpectedType.matches(symbol.returnType isSubTypeOf expectedType)
    }


    private var LookupElement.matchesExpectedType by UserDataProperty(Key<MatchesExpectedType>("MATCHES_EXPECTED_TYPE"))

    enum class MatchesExpectedType {
        MATCHES, NON_TYPABLE, NOT_MATCHES, ;

        companion object {
            fun matches(matches: Boolean) = if (matches) MATCHES else NOT_MATCHES
        }
    }

    const val WEIGHER_ID = "kotlin.expected.type"
}


