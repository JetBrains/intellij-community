// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry.impl

import com.intellij.webSymbols.webTypes.WebTypesJsonFilesCache
import com.intellij.webSymbols.webTypes.WebTypesSymbolTypeResolver
import com.intellij.webSymbols.webTypes.WebTypesSymbolsContainerBase
import com.intellij.model.Pointer
import java.io.File

internal class WebTypesMockContainerImpl : WebTypesSymbolsContainerBase() {

  fun registerFile(file: File) {
    val webTypes = WebTypesJsonFilesCache.fromUrlNoCache(file.toURI().toString())
    val context = WebTypesJsonOriginImpl(
      webTypes,
      typeResolver = WebTypesSymbolTypeResolver { types ->
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

  override fun createPointer(): Pointer<out WebTypesSymbolsContainerBase> =
    Pointer.hardPointer(this)


}