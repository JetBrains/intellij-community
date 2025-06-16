// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.PolySymbolQueryParams
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Suppress("UNCHECKED_CAST")
abstract class AbstractQueryParamsBuilderImpl<T>() : PolySymbolQueryParams.Builder<T> {
  protected val requiredModifiers: MutableList<PolySymbolModifier> = SmartList<PolySymbolModifier>()
  protected val excludeModifiers: MutableList<PolySymbolModifier> = SmartList<PolySymbolModifier>()

  override fun require(modifier: PolySymbolModifier): T {
    requiredModifiers.add(modifier)
    return this as T
  }

  override fun require(vararg modifiers: PolySymbolModifier): T {
    requiredModifiers.addAll(modifiers)
    return this as T
  }

  override fun require(modifiers: Collection<PolySymbolModifier>): T {
    requiredModifiers.addAll(modifiers)
    return this as T
  }

  override fun exclude(modifier: PolySymbolModifier): T {
    excludeModifiers.add(modifier)
    return this as T
  }

  override fun exclude(vararg modifiers: PolySymbolModifier): T {
    excludeModifiers.addAll(modifiers)
    return this as T
  }

  override fun exclude(modifiers: Collection<PolySymbolModifier>): T {
    excludeModifiers.addAll(modifiers)
    return this as T
  }
}