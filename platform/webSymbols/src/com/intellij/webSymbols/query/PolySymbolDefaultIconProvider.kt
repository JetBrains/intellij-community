// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.PolySymbol
import javax.swing.Icon

interface PolySymbolDefaultIconProvider {

  fun getDefaultIcon(namespace: SymbolNamespace, kind: SymbolKind): Icon?

  companion object {

    private val EP_NAME = ExtensionPointName<PolySymbolDefaultIconProvider>("com.intellij.webSymbols.defaultIconProvider")

    fun get(namespace: SymbolNamespace, kind: SymbolKind): Icon? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultIcon(namespace, kind) }
      ?: when (namespace) {
        PolySymbol.NAMESPACE_HTML -> when (kind) {
          PolySymbol.KIND_HTML_ELEMENTS -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag)
          PolySymbol.KIND_HTML_ATTRIBUTES -> AllIcons.Nodes.ObjectTypeAttribute
          else -> null
        }
        PolySymbol.NAMESPACE_JS -> when (kind) {
          PolySymbol.KIND_JS_PROPERTIES -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
          else -> null
        }
        else -> null
      }
  }
}