// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.WebSymbol
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface WebTypesSymbol : PsiSourcedWebSymbol {

  val location: Location?

  override val origin: WebTypesJsonOrigin

  sealed interface Location

  interface FileLocation {
    val fileName: String
    val context: List<VirtualFile>

    fun findFile(): VirtualFile? =
      context.firstNotNullOfOrNull {
        it.parent?.findFileByRelativePath(fileName)
      }

  }

  data class ModuleExport(
    val moduleName: String,
    val symbolName: String,
  ) : Location

  data class FileExport(
    override val fileName: String,
    val symbolName: String,
    override val context: List<VirtualFile>,
  ) : Location, FileLocation

  data class FileOffset(
    override val fileName: String,
    val offset: Int,
    override val context: List<VirtualFile>,
  ) : Location, FileLocation

  companion object {
    val WEB_TYPES_JS_FORBIDDEN_GLOBAL_KINDS = setOf(
      WebSymbol.KIND_JS_PROPERTIES, WebSymbol.KIND_JS_STRING_LITERALS
    )
  }

}