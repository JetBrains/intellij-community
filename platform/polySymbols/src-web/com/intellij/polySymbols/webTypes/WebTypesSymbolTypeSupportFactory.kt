// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.webTypes.impl.WebTypesSymbolTypeSupportFactoryEP
import com.intellij.polySymbols.webTypes.json.WebTypes
import com.intellij.polySymbols.webTypes.json.jsTypesSyntaxWithLegacy
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface WebTypesSymbolTypeSupportFactory {

  fun createTypeSupport(webTypes: WebTypes, project: Project?, context: List<VirtualFile>): PolySymbolTypeSupport

  companion object {

    private const val DEFAULT_TYPE_SYNTAX = "typescript"

    @JvmStatic
    fun get(webTypes: WebTypes, project: Project? = null, context: List<VirtualFile> = emptyList()): PolySymbolTypeSupport =
      (webTypes.jsTypesSyntaxWithLegacy?.name ?: DEFAULT_TYPE_SYNTAX)
        .let { syntax -> WebTypesSymbolTypeSupportFactoryEP.EP_NAME.forKey(syntax.lowercase(Locale.US)) }
        .map { it.createTypeSupport(webTypes, project, context) }
        .let {
          when {
            it.isEmpty() -> EmptySupport
            else -> it[0]
          }
        }

    private object EmptySupport : PolySymbolTypeSupport {
      override val typeProperty: PolySymbolProperty<*>? get() = null
      override fun resolve(types: List<PolySymbolTypeSupport.TypeReference>): Any? = null
    }

  }

}