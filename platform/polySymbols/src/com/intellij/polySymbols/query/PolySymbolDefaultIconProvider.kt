// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import javax.swing.Icon

interface PolySymbolDefaultIconProvider {

  fun getDefaultIcon(qualifiedKind: PolySymbolQualifiedKind): Icon?

  companion object {

    private val EP_NAME = ExtensionPointName<PolySymbolDefaultIconProvider>("com.intellij.polySymbols.defaultIconProvider")

    fun get(qualifiedKind: PolySymbolQualifiedKind): Icon? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultIcon(qualifiedKind) }
      ?: when (qualifiedKind) {
        PolySymbol.HTML_ELEMENTS -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag)
        PolySymbol.HTML_ATTRIBUTES -> AllIcons.Nodes.ObjectTypeAttribute
        PolySymbol.JS_PROPERTIES -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
        else -> null
      }
  }
}