// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.polySymbols.PolySymbolAccessModifier
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.PolySymbolsQueryParams
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Suppress("UNCHECKED_CAST")
abstract class AbstractQueryParamsBuilderImpl<T>() : PolySymbolsQueryParams.Builder<T> {
  protected val requiredModifiers = SmartList<PolySymbolModifier>()
  protected var requiredAccessModifier: PolySymbolAccessModifier? = null
  protected val excludeModifiers = SmartList<PolySymbolModifier>()
  protected val excludeAccessModifiers = SmartList<PolySymbolAccessModifier>()

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

  override fun requireAccess(modifier: PolySymbolAccessModifier): T {
    requiredAccessModifier = modifier
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

  override fun excludeAccess(modifier: PolySymbolAccessModifier): T {
    excludeAccessModifiers.add(modifier)
    return this as T
  }

  override fun excludeAccess(vararg modifiers: PolySymbolAccessModifier): T {
    excludeAccessModifiers.addAll(modifiers)
    return this as T
  }

  override fun excludeAccess(modifiers: Collection<PolySymbolAccessModifier>): T {
    excludeAccessModifiers.addAll(modifiers)
    return this as T
  }
}