// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.webTypes.WebTypesJsonFilesCache
import com.intellij.webSymbols.webTypes.WebTypesSymbolTypeSupport
import com.intellij.webSymbols.webTypes.WebTypesSymbolsScopeBase
import java.io.File

internal class WebTypesMockScopeImpl : WebTypesSymbolsScopeBase() {

  fun registerFile(file: File) {
    val webTypes = WebTypesJsonFilesCache.fromUrlNoCache(file.toURI().toString())
    val context = WebTypesJsonOriginImpl(
      webTypes,
      typeSupport = object : WebTypesSymbolTypeSupport {
        override fun resolve(types: List<WebTypesSymbolTypeSupport.TypeReference>): Any =
          types.map {
            if (it.module != null) "${it.module}:${it.name}"
            else it.name
          }.let {
            if (it.size == 1) it[0] else it
          }
      }
    )
    addWebTypes(webTypes, context)
  }

  override fun createPointer(): Pointer<out WebTypesSymbolsScopeBase> =
    Pointer.hardPointer(this)

}