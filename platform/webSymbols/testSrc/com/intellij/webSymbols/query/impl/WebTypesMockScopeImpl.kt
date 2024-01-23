// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.webTypes.WebTypesJsonFilesCache
import com.intellij.webSymbols.webTypes.WebTypesScopeBase
import java.io.File

internal class WebTypesMockScopeImpl : WebTypesScopeBase() {

  fun registerFile(file: File) {
    val webTypes = WebTypesJsonFilesCache.fromUrlNoCache(file.toURI().toString())
    val context = WebTypesJsonOriginImpl(
      webTypes,
      typeSupport = WebSymbolsMockTypeSupport,
      project = null
    )
    addWebTypes(webTypes, context)
  }

  override fun createPointer(): Pointer<out WebTypesScopeBase> =
    Pointer.hardPointer(this)

}