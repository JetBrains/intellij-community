// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.polySymbols.impl.PolySymbolOriginImpl
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import javax.swing.Icon

interface PolySymbolOrigin {

  val typeSupport: PolySymbolTypeSupport?
    get() = null

  fun loadIcon(path: String): Icon? = null

  companion object {
    @JvmStatic
    fun create(
      typeSupport: PolySymbolTypeSupport? = null,
    ): PolySymbolOrigin =
      PolySymbolOriginImpl(typeSupport)

    @JvmStatic
    fun empty(): PolySymbolOrigin =
      PolySymbolOriginImpl.empty
  }

}