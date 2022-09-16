package com.intellij.javascript.web.refactoring

import com.intellij.javascript.web.symbols.WebSymbol
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