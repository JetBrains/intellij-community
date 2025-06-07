// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes

import com.intellij.model.Pointer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbol.Companion.PROP_NO_DOC
import com.intellij.polySymbols.documentation.PolySymbolWithDocumentation
import com.intellij.polySymbols.documentation.impl.PolySymbolDocumentationTargetImpl
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.psi.PsiElement

interface WebTypesSymbol : PsiSourcedPolySymbol, PolySymbolWithDocumentation {

  val location: Location?

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    if (properties[PROP_NO_DOC] != true)
      PolySymbolDocumentationTargetImpl(this, location)
    else
      null

  override fun createPointer(): Pointer<out WebTypesSymbol>

  sealed interface Location

  sealed interface FileLocation {
    val fileName: String
    val context: List<VirtualFile>

    fun findFile(): VirtualFile? =
      context.firstNotNullOfOrNull {
        it.parent?.findFileByRelativePath(fileName)
      }

  }

  sealed interface ModuleExport : Location {
    val moduleName: String
    val symbolName: String

    companion object {
      @JvmStatic
      fun create(
        moduleName: String,
        symbolName: String,
      ): ModuleExport =
        ModuleExportData(moduleName, symbolName)
    }
  }

  sealed interface FileExport : Location, FileLocation {
    override val fileName: String
    val symbolName: String
    override val context: List<VirtualFile>

    companion object {
      @JvmStatic
      fun create(
        fileName: String,
        symbolName: String,
        context: List<VirtualFile>,
      ): FileExport =
        FileExportData(fileName, symbolName, context)
    }
  }

  sealed interface FileOffset : Location, FileLocation {
    override val fileName: String
    val offset: Int
    override val context: List<VirtualFile>

    companion object {
      @JvmStatic
      fun create(
        fileName: String,
        offset: Int,
        context: List<VirtualFile>,
      ): FileOffset =
        FileOffsetData(fileName, offset, context)

    }
  }

  companion object {
    internal val WEB_TYPES_JS_FORBIDDEN_GLOBAL_KINDS = setOf(
      PolySymbol.JS_PROPERTIES.kind, PolySymbol.JS_STRING_LITERALS.kind
    )
  }

}

private data class ModuleExportData(
  override val moduleName: String,
  override val symbolName: String,
) : WebTypesSymbol.ModuleExport

private data class FileExportData(
  override val fileName: String,
  override val symbolName: String,
  override val context: List<VirtualFile>,
) : WebTypesSymbol.FileExport

private data class FileOffsetData(
  override val fileName: String,
  override val offset: Int,
  override val context: List<VirtualFile>,
) : WebTypesSymbol.FileOffset