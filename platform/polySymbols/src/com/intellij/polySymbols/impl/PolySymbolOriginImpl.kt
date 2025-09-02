// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.PolySymbolOrigin
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import javax.swing.Icon

internal data class PolySymbolOriginImpl(
  override val framework: FrameworkId?,
  override val library: String?,
  override val version: String?,
  override val defaultIcon: Icon?,
  override val typeSupport: PolySymbolTypeSupport?,
) : PolySymbolOrigin {
  companion object {
    val empty = PolySymbolOriginImpl(null, null, null, null, null)
  }
}