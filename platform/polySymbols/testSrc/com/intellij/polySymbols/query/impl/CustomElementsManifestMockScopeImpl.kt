// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.mock.MockProjectEx
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.polySymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.polySymbols.customElements.readCustomElementsManifest
import java.io.File

internal class CustomElementsManifestMockScopeImpl(private val disposable: Disposable) : CustomElementsManifestScopeBase() {

  fun registerFile(file: File) {
    val manifest = readCustomElementsManifest(file.toString())
    val context = CustomElementsManifestJsonOriginImpl(
      file.name.takeWhile { it != '.' },
      project = MockProjectEx(disposable),
      typeSupport = PolySymbolsMockTypeSupport
    )
    addCustomElementsManifest(manifest, context)
  }

  override fun createPointer(): Pointer<CustomElementsManifestMockScopeImpl> =
    Pointer.hardPointer(this)

}