// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
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
            symbol != null -> matchesExpectedType(symbol, context.expectedType)
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
        expectedType: KaType?
    ) = when {
        expectedType == null -> MatchesExpectedType.NON_TYPABLE
        symbol is KaClassSymbol && expectedType.expandedSymbol?.let { symbol.isSubClassOf(it) } == true ->
            MatchesExpectedType.MATCHES

        symbol !is KaCallableSymbol -> MatchesExpectedType.NON_TYPABLE
        expectedType.isUnitType -> MatchesExpectedType.MATCHES
        else -> MatchesExpectedType.matches(symbol.returnType, expectedType)
    }

    private var LookupElement.matchesExpectedType by UserDataProperty(Key<MatchesExpectedType>("MATCHES_EXPECTED_TYPE"))

    enum class MatchesExpectedType {
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
                actualType isPossiblySubTypeOf expectedType -> MATCHES
                actualType.withNullability(KaTypeNullability.NON_NULLABLE) isPossiblySubTypeOf expectedType -> MATCHES_WITHOUT_NULLABILITY
                else -> NOT_MATCHES
            }
        }
    }

    const val WEIGHER_ID = "kotlin.expected.type"
}


