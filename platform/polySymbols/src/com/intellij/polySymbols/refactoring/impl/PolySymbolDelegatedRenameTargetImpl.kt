// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.refactoring.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.refactoring.PolySymbolRenameTarget
import com.intellij.polySymbols.utils.PolySymbolDelegate
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameValidator
import com.intellij.refactoring.rename.api.ReplaceTextTarget
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext

/**
 * Used when creating a [PolySymbolRenameTarget] in [PolySymbolDelegate]. Allows to wrap a [RenameTarget] symbol
 * as a [PolySymbolRenameTarget], while exposing original behavior to the platform.
 */
internal class PolySymbolDelegatedRenameTargetImpl(override val symbol: PolySymbol) : PolySymbolRenameTarget {

  init {
    assert(symbol is RenameTarget)
  }

  override fun createPointer(): Pointer<out PolySymbolRenameTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { PolySymbolDelegatedRenameTargetImpl(it) }
    }
  }

  override val targetName: String
    get() = (symbol as RenameTarget).targetName

  override fun presentation(): TargetPresentation =
    (symbol as RenameTarget).presentation()

  override val maximalSearchScope: SearchScope?
    get() = (symbol as RenameTarget).maximalSearchScope

  override fun textTargets(context: ReplaceTextTargetContext): Collection<ReplaceTextTarget> =
    (symbol as RenameTarget).textTargets(context)

  override fun validator(): RenameValidator =
    (symbol as RenameTarget).validator()

  override fun equals(other: Any?): Boolean =
    other is PolySymbolDelegatedRenameTargetImpl && other.symbol == symbol

  override fun hashCode(): Int =
    symbol.hashCode()
}