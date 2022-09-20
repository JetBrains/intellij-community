// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import javax.swing.Icon

interface WebSymbolDefaultIconProvider {

  fun getDefaultIcon(namespace: WebSymbolsContainer.Namespace, kind: SymbolKind): Icon?

  companion object {

    private val EP_NAME = ExtensionPointName<WebSymbolDefaultIconProvider>("com.intellij.javascript.web.defaultIconProvider")

    fun getDefaultIcon(namespace: WebSymbolsContainer.Namespace, kind: SymbolKind): Icon? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultIcon(namespace, kind) }
      ?: when (namespace) {
        WebSymbolsContainer.Namespace.HTML -> when (kind) {
          WebSymbol.KIND_HTML_ELEMENTS -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag)
          WebSymbol.KIND_HTML_ATTRIBUTES -> AllIcons.Nodes.ObjectTypeAttribute
          else -> null
        }
        WebSymbolsContainer.Namespace.JS -> when (kind) {
          WebSymbol.KIND_JS_PROPERTIES -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
          else -> null
        }
        else -> null
      }
  }
}