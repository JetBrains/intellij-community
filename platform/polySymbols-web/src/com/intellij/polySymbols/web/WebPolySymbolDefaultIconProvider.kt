// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.web

import com.intellij.icons.AllIcons
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.js.JS_PROPERTIES
import com.intellij.polySymbols.query.PolySymbolDefaultIconProvider
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import javax.swing.Icon

internal class WebPolySymbolDefaultIconProvider : PolySymbolDefaultIconProvider {
  override fun getDefaultIcon(kind: PolySymbolKind): Icon? =
    when (kind) {
      HTML_ELEMENTS -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag)
      HTML_ATTRIBUTES -> AllIcons.Nodes.ObjectTypeAttribute
      JS_PROPERTIES -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
      else -> null
    }
}
