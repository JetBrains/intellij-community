// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathUtilRt
import com.intellij.util.io.URLUtil
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsProjectLoader

/**
 * The path here must be system-independent.
 */
data class ModulePath(val path: String, val group: String?) {
  /**
   * Module name (without a file extension)
   */
  val moduleName: String = getModuleNameByFilePath(path)
  
  companion object {
    fun getModuleNameByFilePath(path: String): String {
      return PathUtilRt.getFileName(path).removeSuffix(".iml")
    }

    fun getPathsToModuleFiles(element: Element): Set<ModulePath> {
      val modules = element.getChild(JpsProjectLoader.MODULES_TAG) ?: return emptySet()

      val children = modules.getChildren(JpsProjectLoader.MODULE_TAG)
      val paths = LinkedHashSet<ModulePath>(children.size)
      for (moduleElement in children) {
        val fileUrlValue = moduleElement.getAttributeValue(JpsProjectLoader.FILE_URL_ATTRIBUTE)
        val filepath = if (fileUrlValue == null) {
          // support for older formats
          moduleElement.getAttributeValue(JpsProjectLoader.FILE_PATH_ATTRIBUTE)
        }
        else {
          URLUtil.extractPath(fileUrlValue)
        }
        paths.add(ModulePath(path = FileUtilRt.toSystemIndependentName(filepath!!),
                             group = moduleElement.getAttributeValue(JpsProjectLoader.GROUP_ATTRIBUTE)))
      }
      return paths
    }
  }
}