// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.js.JS_PROPERTIES
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon

interface PolySymbolDefaultIconProvider {

  fun getDefaultIcon(qualifiedKind: PolySymbolQualifiedKind): Icon?

  companion object {

    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolDefaultIconProvider> =
      ExtensionPointName<PolySymbolDefaultIconProvider>("com.intellij.polySymbols.defaultIconProvider")

    @Suppress("TestOnlyProblems")
    fun get(qualifiedKind: PolySymbolQualifiedKind): Icon? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultIcon(qualifiedKind) }
      ?: when (qualifiedKind) {
        HTML_ELEMENTS -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Tag)
        HTML_ATTRIBUTES -> AllIcons.Nodes.ObjectTypeAttribute
        JS_PROPERTIES -> IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)
        else -> null
      }
  }
}