// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import javax.swing.Icon

interface WebSymbolDefaultIconProvider {

  fun getDefaultIcon(namespace: SymbolNamespace, kind: SymbolKind): Icon?

  companion object {

    private val EP_NAME = ExtensionPointName<WebSymbolDefaultIconProvider>("com.intellij.webSymbols.defaultIconProvider")

    fun get(namespace: SymbolNamespace, kind: SymbolKind): Icon? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultIcon(namespace, kind) }
      ?: when (namespace) {
        WebSymbol.NAMESPACE_HTML -> when (kind) {
          WebSymbol.KIND_HTML_ELEMENTS -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag)
          WebSymbol.KIND_HTML_ATTRIBUTES -> AllIcons.Nodes.ObjectTypeAttribute
          else -> null
        }
        WebSymbol.NAMESPACE_JS -> when (kind) {
          WebSymbol.KIND_JS_PROPERTIES -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
          else -> null
        }
        else -> null
      }
  }
}