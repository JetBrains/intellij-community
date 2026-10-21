// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.patterns.PolySymbolFilter
import com.intellij.polySymbols.patterns.ReferenceBuilder
import com.intellij.polySymbols.query.PolySymbolNameConversionRules

internal class ReferenceBuilderImpl : ReferenceBuilder {

  internal var filterValue: PolySymbolFilter? = null
  internal var excludeModifiersValue: MutableList<PolySymbolModifier> = mutableListOf()
  internal val nameConversionRules: MutableList<PolySymbolNameConversionRules> = mutableListOf()

  override fun filter(value: PolySymbolFilter?) {
    filterValue = value
  }

  override fun excludeModifiers(vararg value: PolySymbolModifier) {
    excludeModifiersValue.addAll(value)
  }

  override fun excludeModifiers(value: List<PolySymbolModifier>) {
    excludeModifiersValue.addAll(value)
  }

  override fun nameConversion(rules: PolySymbolNameConversionRules) {
    nameConversionRules += rules
  }

  override fun nameConversion(rules: Collection<PolySymbolNameConversionRules>) {
    nameConversionRules += rules
  }
}
