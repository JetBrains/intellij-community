// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.symbol.SymbolSearchTargetFactory
import com.intellij.openapi.project.Project
import com.intellij.polySymbols.PolySymbol
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PolySymbolSearchTargetFactory : SymbolSearchTargetFactory<PolySymbol> {
  override fun searchTarget(project: Project, symbol: PolySymbol): SearchTarget? =
    if (symbol !is SearchTarget) symbol.searchTarget else null
}