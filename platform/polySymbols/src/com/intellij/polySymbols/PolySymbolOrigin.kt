// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.polySymbols.impl.PolySymbolOriginImpl
import javax.swing.Icon

interface PolySymbolOrigin {

  fun loadIcon(path: String): Icon? = null

  companion object {

    @JvmStatic
    fun empty(): PolySymbolOrigin =
      PolySymbolOriginImpl.empty
  }

}