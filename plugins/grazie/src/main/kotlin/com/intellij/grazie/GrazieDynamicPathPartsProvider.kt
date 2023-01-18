package com.intellij.grazie

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

// Used in ReSharper
interface GrazieDynamicPathPartsProvider {
  companion object {
    private val EP_NAME = ExtensionPointName<GrazieDynamicPathPartsProvider>("com.intellij.grazie.dynamic.pathPartsProvider")
    fun getDynamicFolder(): Path {
      var result = Path.of(PathManager.getSystemPath())
      EP_NAME.extensionList.forEach { pathProvider ->
        result = result.resolve(pathProvider.getDynamicPathParts())
      }
      return result.resolve("grazie")
    }
  }
  fun getDynamicPathParts(): String
}