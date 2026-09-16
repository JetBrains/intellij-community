// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolDsl
import com.intellij.polySymbols.PolySymbolDslBuilderBase
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.patterns.impl.PolySymbolPatternBuilderImpl
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolScope
import org.jetbrains.annotations.ApiStatus

/**
 * Builds a [PolySymbolPattern].
 *
 * The body must produce at least one pattern. Multiple top-level items are
 * wrapped in an implicit sequence; use [PolySymbolPatternBuilder.sequence] to
 * be explicit.
 */
fun polySymbolPattern(body: PolySymbolPatternBuilder.() -> Unit): PolySymbolPattern =
  PolySymbolPatternBuilderImpl().apply(body).buildSingle()

@PolySymbolDsl
@ApiStatus.NonExtendable
interface PolySymbolPatternBuilder {

  /** Literal string match. */
  fun literal(text: String)

  /** Regex match. */
  fun regex(pattern: String, caseSensitive: Boolean = false)

  /**
   * Symbol reference placeholder. Resolves against the enclosing
   * `symbols { }` or custom `symbolsResolver`.
   */
  fun symbolReference(label: String? = null)

  /**
   * Completion auto-popup trigger. The already input name prefix is discarded at this position.
   */
  fun completionPopup()

  /**
   * Completion auto-popup trigger, which keeps the already input name prefix
   * on each completion item.
   */
  fun completionPopupWithPrefixKept()

  /** Reference to a specific symbol resolved along [path]. */
  fun symbolReference(vararg path: PolySymbolQualifiedName)

  /** Reference to a specific symbol resolved along [path]. */
  fun symbolReference(path: List<PolySymbolQualifiedName>)

  /** Ordered sequence; all children must match in order. */
  fun sequence(body: PolySymbolPatternBuilder.() -> Unit)

  /** Alternatives; match exactly one of the `branch { }` blocks. */
  fun oneOf(body: AlternativesBuilder.() -> Unit)

  /** Pattern group with options and/or a symbol resolver. */
  fun group(body: GroupPatternBuilder.() -> Unit)

  /** Optional group. */
  fun optional(body: GroupPatternBuilder.() -> Unit)

  /** Repeating group. */
  fun repeating(body: RepeatingGroupPatternBuilder.() -> Unit)

  /** Optional repeating group. */
  fun optionalRepeating(body: RepeatingGroupPatternBuilder.() -> Unit)
}

@PolySymbolDsl
@ApiStatus.NonExtendable
interface AlternativesBuilder {
  fun branch(body: PolySymbolPatternBuilder.() -> Unit)
}

@PolySymbolDsl
@ApiStatus.NonExtendable
interface GroupPatternBuilder : PolySymbolPatternBuilder {

  fun priority(value: PolySymbol.Priority?)

  fun apiStatus(value: PolySymbolApiStatus?)

  /**
   * Specify property overrides for the resulting [com.intellij.polySymbols.query.PolySymbolMatch].
   *
   * When a pattern evaluates to a match, the resulting symbol's properties are
   * aggregated from the matched symbols, iterated right-to-left (so later
   * segments take precedence over earlier ones). This block installs a
   * synthetic zero-range segment at the end of the match, allowing you to
   * override core properties (`priority`, `apiStatus`, `modifiers`, `icon`)
   * and any custom [PolySymbolProperty].
   */
  fun overrideMatchProperties(body: MatchPropertyOverridesBuilder.() -> Unit)

  /** Append additional scopes made available while matching this group's children. */
  fun additionalScope(vararg scopes: PolySymbolScope)

  /** Append additional scopes made available while matching this group's children. */
  fun additionalScope(scopes: Collection<PolySymbolScope>)

  /** Symbol resolver built from one or more `from(kind, ...)` entries. */
  fun symbols(body: SymbolsBuilder.() -> Unit)

  /**
   * Direct access to a custom [PolySymbolPatternSymbolsResolver]. Mutually
   * exclusive with the [symbols] block; set this when you need a specialized
   * resolver.
   */
  fun symbolsResolver(value: PolySymbolPatternSymbolsResolver?)
}

@PolySymbolDsl
@ApiStatus.NonExtendable
interface RepeatingGroupPatternBuilder : GroupPatternBuilder {
  fun unique(value: Boolean)
}

@PolySymbolDsl
@ApiStatus.NonExtendable
interface SymbolsBuilder {

  /** Add a reference to symbols of the given [kind], optionally scoped under [location]. */
  fun from(
    kind: PolySymbolKind,
    location: List<PolySymbolQualifiedName> = emptyList(),
    body: ReferenceBuilder.() -> Unit = {},
  )
}

@PolySymbolDsl
@ApiStatus.NonExtendable
interface ReferenceBuilder {

  fun filter(value: PolySymbolFilter?)

  fun excludeModifiers(vararg value: PolySymbolModifier)

  fun excludeModifiers(value: List<PolySymbolModifier>)

  fun nameConversion(rules: PolySymbolNameConversionRules)

  fun nameConversion(rules: Collection<PolySymbolNameConversionRules>)
}

@PolySymbolDsl
@ApiStatus.NonExtendable
interface MatchPropertyOverridesBuilder : PolySymbolDslBuilderBase
