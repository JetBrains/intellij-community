// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.polySymbols.PolySymbolModifier
import java.util.concurrent.ConcurrentHashMap

class PolySymbolModifierData(override val name: String) : PolySymbolModifier {

  override fun toString(): String = name

  companion object {

    private val registeredModifiers = ConcurrentHashMap<PolySymbolModifier, PolySymbolModifier>()

    fun create(name: String): PolySymbolModifier =
      registeredModifiers.computeIfAbsent(PolySymbolModifierData(name)) { it }

  }
}