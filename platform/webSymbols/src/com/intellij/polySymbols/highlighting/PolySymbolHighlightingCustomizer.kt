// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.highlighting

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind

interface PolySymbolHighlightingCustomizer {

  fun getSymbolKindTextAttributes(qualifiedKind: PolySymbolQualifiedKind): TextAttributesKey? = null

  fun getDefaultHostClassTextAttributes(): Map<Class<out PsiExternalReferenceHost>, TextAttributesKey> = emptyMap()

  fun getSymbolTextAttributes(host: PsiExternalReferenceHost, symbol: PolySymbol, level: Int): TextAttributesKey? = null

  companion object {

    internal val EP_NAME: ExtensionPointName<PolySymbolHighlightingCustomizer> =
      ExtensionPointName<PolySymbolHighlightingCustomizer>("com.intellij.webSymbols.highlightingCustomizer")

    internal fun getSymbolTextAttributes(host: PsiExternalReferenceHost, symbol: PolySymbol, level: Int): TextAttributesKey? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getSymbolTextAttributes(host, symbol, level) }

    internal fun getTextAttributesFor(kind: PolySymbolQualifiedKind): TextAttributesKey? =
      EP_NAME.computeIfAbsent(kind, PolySymbolHighlightingCustomizer::class.java) { kind ->
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