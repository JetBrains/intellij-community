// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.completion.KeywordLookupObject
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.NamedArgumentLookupObject
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.UserDataProperty


internal object ExpectedTypeWeigher {
    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): MatchesExpectedType =
            element.matchesExpectedType ?: MatchesExpectedType.NON_TYPABLE
    }

    context(KaSession)
    fun addWeight(context: WeighingContext, lookupElement: LookupElement, symbol: KaSymbol?) {
        val expectedType = context.expectedType

        lookupElement.matchesExpectedType = when {
            symbol != null -> if (expectedType != null) matchesExpectedType(symbol, expectedType) else MatchesExpectedType.NON_TYPABLE
            lookupElement.`object` is NamedArgumentLookupObject -> MatchesExpectedType.MATCHES
            lookupElement.`object` is KeywordLookupObject && expectedType != null -> {
                val actualType = when (lookupElement.lookupString) {
                    KtTokens.NULL_KEYWORD.value -> buildClassType(DefaultTypeClassIds.NOTHING).withNullability(KaTypeNullability.NULLABLE)

                    KtTokens.TRUE_KEYWORD.value,
                    KtTokens.FALSE_KEYWORD.value -> buildClassType(DefaultTypeClassIds.BOOLEAN)

                    else -> null
                } ?: return

                MatchesExpectedType.matches(actualType, expectedType)
            }

            else -> null
        }
    }

    context(KaSession)
    private fun matchesExpectedType(
        symbol: KaSymbol,
        expectedType: KaType,
    ) = when {
        symbol is KaEnumEntrySymbol && expectedType.isEnum() && symbol.returnType.isSubtypeOf(expectedType) ->
            MatchesExpectedType.MATCHES_PREFERRED

        symbol is KaClassSymbol && expectedType.expandedSymbol?.let { symbol == it || symbol.isSubClassOf(it) } == true ->
            MatchesExpectedType.MATCHES

        symbol !is KaCallableSymbol -> MatchesExpectedType.NON_TYPABLE

        expectedType.isUnitType -> MatchesExpectedType.MATCHES
        else -> MatchesExpectedType.matches(symbol.returnType, expectedType)
    }

    internal var LookupElement.matchesExpectedType by UserDataProperty(Key<MatchesExpectedType>("MATCHES_EXPECTED_TYPE"))

    @Serializable
    enum class MatchesExpectedType {
        MATCHES_PREFERRED, // Matches and is also more likely to be something the user wants to use (e.g. enum entries when an enum is expected)
        MATCHES,

        /**
         * Actual type would match expected type if it was non-nullable.
         */
        MATCHES_WITHOUT_NULLABILITY,
        NON_TYPABLE,
        NOT_MATCHES,
        ;

        companion object {
            context(KaSession)
            fun matches(actualType: KaType, expectedType: KaType): MatchesExpectedType = when {
                // We exclude the Nothing type because it would match everything, but we should not give it priority.
                // The only exception where we should prefer is for the `null` constant, which will be of type `Nothing?`
                actualType.isNothingType && !actualType.isMarkedNullable -> NOT_MATCHES
                actualType.isSubtypeOf(expectedType) -> MATCHES
                actualType.withNullability(KaTypeNullability.NON_NULLABLE).isSubtypeOf(expectedType) -> MATCHES_WITHOUT_NULLABILITY
                else -> NOT_MATCHES
            }
        }
    }

    const val WEIGHER_ID = "kotlin.expected.type"
}


