// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.isNothingType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.isSubClassOf
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.components.upperBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
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

    context(_: KaSession)
    fun addWeight(context: WeighingContext, lookupElement: LookupElement, symbol: KaSymbol?) {
        // In case of flexible types, we choose the upper bound here to be more lenient for weighing purposes
        val expectedType = context.expectedType?.upperBoundIfFlexible()

        // The expected type was already set elsewhere, we prefer these results
        if (lookupElement.matchesExpectedType != null) return

        lookupElement.matchesExpectedType = when {
            symbol != null -> if (expectedType != null) {
                // If the symbol is a Typealias, we want to use the original symbol for matching the expected type
                val expandedSymbol = (symbol as? KaTypeAliasSymbol)?.expandedType?.expandedSymbol ?: symbol
                matchesExpectedType(expandedSymbol, expectedType)
            } else MatchesExpectedType.NON_TYPABLE
            lookupElement.`object` is NamedArgumentLookupObject -> MatchesExpectedType.MATCHES
            lookupElement.`object` is KeywordLookupObject && expectedType != null -> {
                val actualType = when (lookupElement.lookupString) {
                    KtTokens.NULL_KEYWORD.value -> buildClassType(DefaultTypeClassIds.NOTHING).withNullability(true)

                    KtTokens.TRUE_KEYWORD.value,
                    KtTokens.FALSE_KEYWORD.value -> buildClassType(DefaultTypeClassIds.BOOLEAN)

                    else -> null
                } ?: return

                MatchesExpectedType.matches(actualType, expectedType)
            }

            else -> null
        }
    }

    context(_: KaSession)
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
        /**
         * Actual type would match the expected type if we ignore unsubstituted type parameters in the expected type.
         */
        MATCHES_IGNORING_TYPE_ARGUMENTS,
        NON_TYPABLE,
        NOT_MATCHES,
        ;

        companion object {

            /**
             * Checks if [actualType] could be a subtype of [expectedType] by replacing type parameters in [expectedType]
             * with star projections and ignoring nullability.
             * Returns false for cases where either type is just a type parameter because it would result in trivial matches.
             * See: [isPossiblySubTypeOf].
             *
             * In completion, we work with unsubstituted symbols where type parameters are not substituted.
             * This function provides a fast structural compatibility check (e.g., `List<Int>` matches `List<T>`, but not vice versa!)
             * that may produce false positives matches but avoids the performance cost of full constraint resolution.
             */
            context(_: KaSession)
            fun isPossiblySubtype(actualType: KaType, expectedType: KaType): Boolean {
                if (actualType is KaTypeParameterType) return false
                if (expectedType is KaTypeParameterType) return false
                return actualType.withNullability(false).isPossiblySubTypeOf(expectedType)
            }

            context(_: KaSession)
            fun matches(actualType: KaType, expectedType: KaType): MatchesExpectedType = when {
                // We exclude the Nothing type because it would match everything, but we should not give it priority.
                // The only exception where we should prefer is for the `null` constant, which will be of type `Nothing?`
                actualType.isNothingType && !actualType.isMarkedNullable -> NOT_MATCHES

                actualType.isSubtypeOf(expectedType) -> MATCHES

                // This matches for `null`, which should not ever be suggested for non-nullable expecte types
                actualType.isNothingType && actualType.isMarkedNullable && !expectedType.isNullable -> NOT_MATCHES

                actualType.withNullability(false).isSubtypeOf(expectedType) -> MATCHES_WITHOUT_NULLABILITY

                isPossiblySubtype(actualType, expectedType) -> MATCHES_IGNORING_TYPE_ARGUMENTS

                else -> NOT_MATCHES
            }
        }
    }

    const val WEIGHER_ID = "kotlin.expected.type"
}


