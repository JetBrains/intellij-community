// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.polySymbols.PolySymbolAccessModifier
import com.intellij.polySymbols.PolySymbolModifier
import java.util.concurrent.ConcurrentHashMap

class PolySymbolAccessModifierData(override val name: String) : PolySymbolAccessModifier {

  override fun toString(): String = name

  companion object {

    private val registeredModifiers = ConcurrentHashMap<PolySymbolAccessModifier, PolySymbolAccessModifier>()

    fun create(name: String): PolySymbolAccessModifier =
      registeredModifiers.computeIfAbsent(PolySymbolAccessModifierData(name)) { it }

  }
}