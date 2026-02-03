// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.refactoring

import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.polySymbols.PolySymbol
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PolySymbolRenameTargetFactory : SymbolRenameTargetFactory {
  override fun renameTarget(project: Project, symbol: Symbol): RenameTarget? =
    if (symbol is PolySymbol && symbol !is RenameTarget)
      symbol.renameTarget
    else
      null
}