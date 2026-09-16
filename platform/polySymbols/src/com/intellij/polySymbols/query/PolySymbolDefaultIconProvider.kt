// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbolKind
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon

interface PolySymbolDefaultIconProvider {

  fun getDefaultIcon(kind: PolySymbolKind): Icon?

  companion object {

    @TestOnly
    @JvmField
    val EP_NAME: ExtensionPointName<PolySymbolDefaultIconProvider> =
      ExtensionPointName("com.intellij.polySymbols.defaultIconProvider")

    @Suppress("TestOnlyProblems")
    fun get(kind: PolySymbolKind): Icon? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getDefaultIcon(kind) }
  }
}