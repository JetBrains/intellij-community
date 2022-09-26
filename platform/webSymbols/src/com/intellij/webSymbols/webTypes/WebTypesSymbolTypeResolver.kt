// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.webSymbols.webTypes.impl.WebTypesSymbolTypeResolverFactoryEP
import com.intellij.webSymbols.webTypes.json.WebTypes
import com.intellij.webSymbols.webTypes.json.jsTypesSyntaxWithLegacy
import java.util.*

fun interface WebTypesSymbolTypeResolver {

  fun resolveType(types: List<TypeReference>): Any?

  data class TypeReference(
    val module: String?,
    val name: String
  )

  interface Factory {
    fun createResolver(webTypes: WebTypes, project: Project?, context: List<VirtualFile>): WebTypesSymbolTypeResolver
  }

  companion object {

    const val DEFAULT_TYPE_SYNTAX = "typescript"

    @JvmStatic
    fun get(webTypes: WebTypes, project: Project? = null, context: List<VirtualFile> = emptyList()): WebTypesSymbolTypeResolver =
      (webTypes.jsTypesSyntaxWithLegacy?.name ?: DEFAULT_TYPE_SYNTAX)
        .let { syntax -> WebTypesSymbolTypeResolverFactoryEP.EP_NAME.forKey(syntax.lowercase(Locale.US)) }
        .map { it.createResolver(webTypes, project, context) }
        .let {
          when {
            it.isEmpty() -> EmptyResolver
            it.size == 1 -> it[0]
            else -> CompoundResolver(it)
          }
        }

    private object EmptyResolver : WebTypesSymbolTypeResolver {
      override fun resolveType(types: List<TypeReference>): Any? = null

    }

    private class CompoundResolver(val resolvers: List<WebTypesSymbolTypeResolver>) : WebTypesSymbolTypeResolver {
      override fun resolveType(types: List<TypeReference>): Any? =
        resolvers.firstNotNullOfOrNull { it.resolveType(types) }

    }

  }

}