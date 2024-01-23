// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.webSymbols.customElements.readCustomElementsManifest
import java.io.File

internal class CustomElementsManifestMockScopeImpl : CustomElementsManifestScopeBase() {

  fun registerFile(file: File) {
    val manifest = readCustomElementsManifest(file.toString())
    val context = CustomElementsManifestJsonOriginImpl(
      file.name.takeWhile { it != '.' },
      project = null,
      typeSupport = WebSymbolsMockTypeSupport
    )
    addCustomElementsManifest(manifest, context)
  }

  override fun createPointer(): Pointer<CustomElementsManifestMockScopeImpl> =
    Pointer.hardPointer(this)

}