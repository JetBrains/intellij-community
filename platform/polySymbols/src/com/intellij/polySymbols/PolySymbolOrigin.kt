// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.openapi.util.NlsSafe
import com.intellij.polySymbols.impl.PolySymbolOriginImpl
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import javax.swing.Icon

interface PolySymbolOrigin {
  val library: @NlsSafe String?
    get() = null

  val version: @NlsSafe String?
    get() = null

  val typeSupport: PolySymbolTypeSupport?
    get() = null

  fun loadIcon(path: String): Icon? = null

  companion object {
    @JvmStatic
    fun create(
      library: String? = null,
      version: String? = null,
      typeSupport: PolySymbolTypeSupport? = null,
    ): PolySymbolOrigin =
      PolySymbolOriginImpl(library, version, typeSupport)

    @JvmStatic
    fun empty(): PolySymbolOrigin =
      PolySymbolOriginImpl.empty
  }

}