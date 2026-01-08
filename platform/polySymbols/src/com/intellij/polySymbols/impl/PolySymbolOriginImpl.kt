// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.utils.PolySymbolTypeSupport

internal data class PolySymbolOriginImpl(
  override val typeSupport: PolySymbolTypeSupport?,
) : PolySymbolOrigin {
  companion object {
    val empty = PolySymbolOriginImpl(null)
  }
}