// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.mock.MockProjectEx
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.polySymbols.webTypes.WebTypesJsonFilesCache
import com.intellij.polySymbols.webTypes.WebTypesScopeBase
import java.io.File

internal class WebTypesMockScopeImpl(private val disposable: Disposable) : WebTypesScopeBase() {

  fun registerFile(file: File) {
    val webTypes = WebTypesJsonFilesCache.fromUrlNoCache(file.toURI().toString())
    val context = WebTypesJsonOriginImpl(
      webTypes,
      typeSupport = PolySymbolsMockTypeSupport,
      project = MockProjectEx(disposable),
    )
    addWebTypes(webTypes, context)
  }

  override fun createPointer(): Pointer<out WebTypesScopeBase> =
    Pointer.hardPointer(this)

}