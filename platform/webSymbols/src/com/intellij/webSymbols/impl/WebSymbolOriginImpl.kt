// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.impl

import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbolOrigin
import com.intellij.webSymbols.WebSymbolTypeSupport
import javax.swing.Icon

internal data class WebSymbolOriginImpl(override val framework: FrameworkId?,
                                        override val library: String?,
                                        override val version: String?,
                                        override val defaultIcon: Icon?,
                                        override val typeSupport: WebSymbolTypeSupport?) : WebSymbolOrigin {
  companion object {
    val empty = WebSymbolOriginImpl(null, null, null, null, null)
  }
}