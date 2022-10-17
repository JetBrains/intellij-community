// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.refactoring

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.webSymbols.WebSymbol

open class WebSymbolRenameTarget(val symbol: WebSymbol) : RenameTarget {

  override fun createPointer(): Pointer<out RenameTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { WebSymbolRenameTarget(it) }
    }
  }

  override val targetName: String
    get() = symbol.matchedName

  override fun presentation(): TargetPresentation {
    return symbol.presentation
  }
}