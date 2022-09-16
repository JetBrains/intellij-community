package com.intellij.javascript.web.webTypes.registry.impl

import com.intellij.javascript.web.webTypes.WebTypesJsonFilesCache
import com.intellij.javascript.web.webTypes.WebTypesSymbolTypeResolver
import com.intellij.javascript.web.webTypes.WebTypesSymbolsContainerBase
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