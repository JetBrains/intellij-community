// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.impl.DependencyScope
import com.intellij.polySymbols.impl.DependencyScope.Companion.dependencyScope
import com.intellij.polySymbols.impl.DependencySource
import com.intellij.polySymbols.impl.PolySymbolDslBuilderBaseImpl
import com.intellij.polySymbols.patterns.MatchPropertyOverridesBuilder
import javax.swing.Icon

internal class MatchPropertyOverridesBuilderImpl : PolySymbolDslBuilderBaseImpl(), MatchPropertyOverridesBuilder {
  override val builderContext: String get() = "overrideMatchProperties"

  /**
   * Resolve all declared dependencies and build the override symbol. Returns
   * `null` if no overrides were set, or if any declared dependency failed to
   * dereference (in which case the whole override contribution is dropped).
   */
  internal fun build(): PolySymbol? {
    if (priorityGetter == null
        && priorityValue == null
        && apiStatusGetter == null
        && apiStatusValue == null
        && modifiersGetter == null
        && modifiersValue == null
        && iconGetter == null
        && iconValue == null
        && propertyValues.isEmpty()
        && propertyGetters.isEmpty()) {
      return null
    }
    val source = DependencySource.fromSpecs(depSpecs.toList())
    return MatchPropertyOverrideSymbol(
      source = source,
      scope = source.dependencyScope(),
      priorityGetter = priorityGetter,
      priorityValue = priorityValue,
      apiStatusGetter = apiStatusGetter,
      apiStatusValue = apiStatusValue,
      modifiersGetter = modifiersGetter,
      modifiersValue = modifiersValue,
      iconGetter = iconGetter,
      iconValue = iconValue,
      propertyGetters = propertyGetters.toMap(),
      propertyValues = propertyValues.toMap(),
    )
  }
}

private val MATCH_PROPERTY_OVERRIDE_KIND: PolySymbolKind =
  PolySymbolKind["", "\$matchPropertyOverride$"]

private class MatchPropertyOverrideSymbol(
  private val source: DependencySource,
  private val scope: DependencyScope,
  private val priorityValue: PolySymbol.Priority?,
  private val priorityGetter: (() -> PolySymbol.Priority?)?,
  private val apiStatusValue: PolySymbolApiStatus?,
  private val apiStatusGetter: (() -> PolySymbolApiStatus)?,
  private val modifiersValue: Set<PolySymbolModifier>?,
  private val modifiersGetter: (() -> Set<PolySymbolModifier>)?,
  private val iconValue: Icon?,
  private val iconGetter: (() -> Icon?)?,
  private val propertyValues: Map<PolySymbolProperty<*>, Any?>,
  private val propertyGetters: Map<PolySymbolProperty<*>, () -> Any?>,
) : PolySymbol {
  override val kind: PolySymbolKind get() = MATCH_PROPERTY_OVERRIDE_KIND
  override val name: String get() = ""

  override val priority: PolySymbol.Priority?
    get() = priorityGetter?.let { scope.withinScope { it() } }
            ?: priorityValue
            ?: super.priority

  override val apiStatus: PolySymbolApiStatus
    get() = apiStatusGetter?.let { scope.withinScope { it() } }
            ?: apiStatusValue
            ?: super.apiStatus

  override val modifiers: Set<PolySymbolModifier>
    get() = modifiersGetter?.let { scope.withinScope { it() } }
            ?: modifiersValue
            ?: super.modifiers

  override val icon: Icon?
    get() = iconGetter?.let { scope.withinScope { it() } }
            ?: iconValue
            ?: super.icon

  override fun <T : Any> get(property: PolySymbolProperty<T>): T? {
    val getter = propertyGetters[property]
    if (getter != null) {
      @Suppress("UNCHECKED_CAST")
      return property.tryCast(scope.withinScope { (getter as () -> T?).invoke() })
    }
    if (propertyValues.containsKey(property)) {
      return property.tryCast(propertyValues[property])
    }
    return super.get(property)
  }

  override fun createPointer(): Pointer<out PolySymbol> {
    if (source.isEmpty) return Pointer.hardPointer(this)
    val pointerSource = source.asFromPointers()
    val priorityValue = priorityValue
    val priorityGetter = priorityGetter
    val apiStatusValue = apiStatusValue
    val apiStatusGetter = apiStatusGetter
    val modifiersValue = modifiersValue
    val modifiersGetter = modifiersGetter
    val iconValue = iconValue
    val iconGetter = iconGetter
    val propertyValues = propertyValues
    val propertyGetters = propertyGetters
    return Pointer {
      MatchPropertyOverrideSymbol(
        source = pointerSource,
        scope = pointerSource.dependencyScope() ?: return@Pointer null,
        priorityValue = priorityValue,
        priorityGetter = priorityGetter,
        apiStatusValue = apiStatusValue,
        apiStatusGetter = apiStatusGetter,
        modifiersValue = modifiersValue,
        modifiersGetter = modifiersGetter,
        iconValue = iconValue,
        iconGetter = iconGetter,
        propertyValues = propertyValues,
        propertyGetters = propertyGetters,
      )
    }
  }
}
