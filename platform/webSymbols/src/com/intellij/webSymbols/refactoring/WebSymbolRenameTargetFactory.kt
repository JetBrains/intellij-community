// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.refactoring

import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory

class WebSymbolRenameTargetFactory: SymbolRenameTargetFactory {
  override fun renameTarget(project: Project, symbol: Symbol): RenameTarget? =
    (symbol as? RenameableWebSymbol)?.renameTarget
}