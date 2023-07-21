// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.search

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.symbol.SymbolSearchTargetFactory
import com.intellij.openapi.project.Project

class WebSymbolSearchTargetFactory : SymbolSearchTargetFactory<SearchTargetWebSymbol> {
  override fun searchTarget(project: Project, symbol: SearchTargetWebSymbol): SearchTarget? =
    symbol.searchTarget
}