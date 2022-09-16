package com.intellij.javascript.web.findUsages

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.javascript.web.symbols.WebSymbol
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation

open class WebSymbolSearchTarget(val symbol: WebSymbol) : SearchTarget {

  override fun createPointer(): Pointer<out SearchTarget> {
    val symbolPtr = symbol.createPointer()
    return Pointer {
      symbolPtr.dereference()?.let { WebSymbolSearchTarget(it) }
    }
  }

  override val presentation: TargetPresentation
    get() = symbol.presentation

  override val usageHandler: UsageHandler<*>
    get() = UsageHandler.createEmptyUsageHandler(presentation.presentableText)

  override fun equals(other: Any?): Boolean =
    other === this ||
    (other is WebSymbolSearchTarget && other.symbol == symbol)

  override fun hashCode(): Int =
    symbol.hashCode()

}