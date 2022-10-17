// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.webSymbols.WebSymbolTypeSupport
import com.intellij.webSymbols.webTypes.impl.WebTypesSymbolTypeSupportFactoryEP
import com.intellij.webSymbols.webTypes.json.WebTypes
import com.intellij.webSymbols.webTypes.json.jsTypesSyntaxWithLegacy
import java.util.*

interface WebTypesSymbolTypeSupport : WebSymbolTypeSupport {

  fun resolve(types: List<TypeReference>): Any?

  data class TypeReference(
    val module: String?,
    val name: String
  )

  interface Factory {
    fun createTypeSupport(webTypes: WebTypes, project: Project?, context: List<VirtualFile>): WebTypesSymbolTypeSupport
  }

  companion object {

    const val DEFAULT_TYPE_SYNTAX = "typescript"

    @JvmStatic
    fun get(webTypes: WebTypes, project: Project? = null, context: List<VirtualFile> = emptyList()): WebTypesSymbolTypeSupport =
      (webTypes.jsTypesSyntaxWithLegacy?.name ?: DEFAULT_TYPE_SYNTAX)
        .let { syntax -> WebTypesSymbolTypeSupportFactoryEP.EP_NAME.forKey(syntax.lowercase(Locale.US)) }
        .map { it.createTypeSupport(webTypes, project, context) }
        .let {
          when {
            it.isEmpty() -> EmptySupport
            else -> it[0]
          }
        }

    private object EmptySupport : WebTypesSymbolTypeSupport {
      override fun resolve(types: List<TypeReference>): Any? = null

    }

  }

}