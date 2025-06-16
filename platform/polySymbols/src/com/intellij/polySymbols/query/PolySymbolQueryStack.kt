// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbol
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class PolySymbolQueryStack() {

  private val stack = SmartList<PolySymbolScope>()

  constructor(initialStack: Collection<PolySymbolScope>) : this() {
    stack.addAll(initialStack)
  }

  constructor(vararg initialStack: PolySymbolScope) : this() {
    stack.addAll(initialStack)
  }

  internal val lastPolySymbol: PolySymbol?
    get() = stack.lastOrNull { it is PolySymbol } as? PolySymbol

  internal fun peek(): PolySymbolScope? =
    stack.last()

  internal fun toList(): List<PolySymbolScope> =
    stack.toList()

  internal fun copy(): PolySymbolQueryStack =
    PolySymbolQueryStack(stack)

  @ApiStatus.Internal
  @OptIn(ExperimentalContracts::class)
  fun <T> withSymbols(symbols: List<PolySymbolScope>, action: () -> T): T {
    contract {
      callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    if (symbols.isEmpty())
      return action()
    else try {
      stack.addAll(symbols)
      return action()
    }
    finally {
      repeat(symbols.count()) {
        stack.removeLast()
      }
    }
  }

}