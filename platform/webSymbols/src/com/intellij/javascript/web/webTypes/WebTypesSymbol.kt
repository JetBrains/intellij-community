package com.intellij.javascript.web.webTypes

import com.intellij.javascript.web.symbols.PsiSourcedWebSymbol
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface WebTypesSymbol : PsiSourcedWebSymbol {

  val location: Location?

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

}