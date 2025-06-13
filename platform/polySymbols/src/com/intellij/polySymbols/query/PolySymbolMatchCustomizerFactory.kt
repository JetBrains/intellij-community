// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.query.impl.PolySymbolMatchCompoundCustomizer
import org.jetbrains.annotations.TestOnly

interface PolySymbolMatchCustomizerFactory {

  fun create(symbol: PolySymbolMatch): PolySymbolMatchCustomizer?

  companion object {
    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolMatchCustomizerFactory> = ExtensionPointName
      .create<PolySymbolMatchCustomizerFactory>("com.intellij.polySymbols.matchCustomizerFactory")

    @JvmStatic
    fun getPolySymbolMatchCustomizer(symbol: PolySymbolMatch): PolySymbolMatchCustomizer {
      @Suppress("TestOnlyProblems")
      val customizers = EP_NAME.extensionList.mapNotNull { factory ->
        factory.create(symbol)
      }
      return when (customizers.size) {
        0 -> PolySymbolMatchEmptyCustomizer
        1 -> customizers[0]
        else -> PolySymbolMatchCompoundCustomizer(customizers)
      }
    }

    private object PolySymbolMatchEmptyCustomizer : PolySymbolMatchCustomizer

  }
}