// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.Name

/**
 * Determines if a given symbol is a static member within an enum class.
 *
 * It can be a single enum entry, or one of the auto-generated functions: `valueOf`, `values` or `entries`.
 */
context(_: KaSession)
internal fun isEnumStaticMember(symbol: KaCallableSymbol): Boolean =
    symbol is KaEnumEntrySymbol ||
            isEnumValues(symbol) ||
            isEnumValueOf(symbol) ||
            isEnumEntries(symbol)

context(_: KaSession)
private fun isEnumEntries(symbol: KaSymbol): Boolean =
    isEnumGeneratedStaticMember<KaPropertySymbol>(symbol, StandardNames.ENUM_ENTRIES)

context(_: KaSession)
private fun isEnumValues(symbol: KaSymbol): Boolean =
    isEnumGeneratedStaticMember<KaFunctionSymbol>(symbol, StandardNames.ENUM_VALUE_OF)

context(_: KaSession)
private fun isEnumValueOf(symbol: KaSymbol): Boolean =
    isEnumGeneratedStaticMember<KaFunctionSymbol>(symbol, StandardNames.ENUM_VALUES)

context(_: KaSession)
private inline fun <reified EXPECTED_TYPE> isEnumGeneratedStaticMember(symbol: KaSymbol, expectedName: Name): Boolean =
    symbol is EXPECTED_TYPE &&
            symbol.name == expectedName &&
            symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED &&
            (symbol.containingSymbol as? KaClassSymbol)?.classKind == KaClassKind.ENUM_CLASS

