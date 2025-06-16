// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.context.PolyContextRulesProvider
import com.intellij.polySymbols.query.PolySymbolNameConversionRulesProvider
import com.intellij.polySymbols.query.PolySymbolScope

interface StaticPolySymbolScope : PolySymbolScope, PolyContextRulesProvider {

  override fun createPointer(): Pointer<out StaticPolySymbolScope>

  fun getNameConversionRulesProvider(framework: FrameworkId): PolySymbolNameConversionRulesProvider
}