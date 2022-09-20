package com.intellij.webSymbols.refactoring

import com.intellij.webSymbols.WebSymbol
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.refactoring.rename.api.RenameTarget

open class WebSymbolRenameTarget(val symbol: WebSymbol): RenameTarget {

  override fun createPointer(): Pointer<out RenameTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { WebSymbolRenameTarget(it) }
    }
  }

  override val targetName: String
    get() = symbol.matchedName

  override val presentation: TargetPresentation
    get() = symbol.presentation

}