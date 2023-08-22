// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.webSymbols.WebSymbolTypeSupport
import com.intellij.webSymbols.webTypes.impl.WebTypesSymbolTypeSupportFactoryEP
import com.intellij.webSymbols.webTypes.json.WebTypes
import com.intellij.webSymbols.webTypes.json.jsTypesSyntaxWithLegacy
import java.util.*

interface WebTypesSymbolTypeSupportFactory {

  fun createTypeSupport(webTypes: WebTypes, project: Project?, context: List<VirtualFile>): WebSymbolTypeSupport

  companion object {

    private const val DEFAULT_TYPE_SYNTAX = "typescript"

    @JvmStatic
    fun get(webTypes: WebTypes, project: Project? = null, context: List<VirtualFile> = emptyList()): WebSymbolTypeSupport =
      (webTypes.jsTypesSyntaxWithLegacy?.name ?: DEFAULT_TYPE_SYNTAX)
        .let { syntax -> WebTypesSymbolTypeSupportFactoryEP.EP_NAME.forKey(syntax.lowercase(Locale.US)) }
        .map { it.createTypeSupport(webTypes, project, context) }
        .let {
          when {
            it.isEmpty() -> EmptySupport
            else -> it[0]
          }
        }

    private object EmptySupport : WebSymbolTypeSupport {
      override fun resolve(types: List<WebSymbolTypeSupport.TypeReference>): Any? = null
    }

  }

}