package com.intellij.grazie

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path
import java.nio.file.Paths

// Used in ReSharper
abstract class GraziePathProvider {
  companion object {
    val EP_NAME = ExtensionPointName<GraziePathProvider>("com.intellij.grazie.pathProvider")
    fun getDynamicFolder(): Path {
      val extensionList = EP_NAME.extensionList
      if(extensionList.size == 1)
        return extensionList[0].getDynamicFolderPath()
      return Paths.get(PathManager.getSystemPath(), "grazie")
    }
  }
  abstract fun getDynamicFolderPath(): Path
}