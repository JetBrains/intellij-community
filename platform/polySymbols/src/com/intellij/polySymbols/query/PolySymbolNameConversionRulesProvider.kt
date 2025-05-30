// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker

interface PolySymbolNameConversionRulesProvider : ModificationTracker {

  fun getNameConversionRules(): PolySymbolNameConversionRules

  fun createPointer(): Pointer<out PolySymbolNameConversionRulesProvider>

}