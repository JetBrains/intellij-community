// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.highlighting

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind

interface WebSymbolHighlightingCustomizer {

  fun getSymbolKindTextAttributes(qualifiedKind: WebSymbolQualifiedKind): TextAttributesKey? = null

  fun getDefaultHostClassTextAttributes(): Map<Class<out PsiExternalReferenceHost>, TextAttributesKey> = emptyMap()

  fun getSymbolTextAttributes(host: PsiExternalReferenceHost, symbol: WebSymbol, level: Int): TextAttributesKey? = null

  companion object {

    internal val EP_NAME: ExtensionPointName<WebSymbolHighlightingCustomizer> =
      ExtensionPointName<WebSymbolHighlightingCustomizer>("com.intellij.webSymbols.highlightingCustomizer")

    internal fun getSymbolTextAttributes(host: PsiExternalReferenceHost, symbol: WebSymbol, level: Int): TextAttributesKey? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getSymbolTextAttributes(host, symbol, level) }

    internal fun getTextAttributesFor(kind: WebSymbolQualifiedKind): TextAttributesKey? =
      EP_NAME.computeIfAbsent(kind, WebSymbolHighlightingCustomizer::class.java) { kind ->
        listOfNotNull(EP_NAME.extensionList.firstNotNullOfOrNull { it.getSymbolKindTextAttributes(kind) })
      }.firstOrNull()

    internal fun getDefaultHostTextAttributes(host: PsiExternalReferenceHost): TextAttributesKey? {
      val clazz = host::class.java
      return EP_NAME.computeIfAbsent(clazz) {
        listOf(EP_NAME.extensionList.firstNotNullOfOrNull {
          it.getDefaultHostClassTextAttributes().firstNotNullOfOrNull { (keyClass, key) ->
            if (keyClass.isAssignableFrom(clazz)) key else null
          }
        })
      }[0]
    }

  }
}