/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module.impl

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsProjectLoader

/**
 * Path here must be system-independent.
 */
data class ModulePath(val path: String, val group: String?) {
  /**
   * Module name (without file extension)
   */
  val moduleName: String = getModuleNameByFilePath(path)
}

fun getModuleNameByFilePath(path: String): String {
  return PathUtil.getFileName(path).removeSuffix(ModuleFileType.DOT_DEFAULT_EXTENSION)
}

internal abstract class SaveItem {
  protected abstract val moduleName: String
  protected abstract val moduleFilePath: String

  protected abstract val groupPathString: String?

  fun writeExternal(parentElement: Element) {
    val moduleFilePath = moduleFilePath
    // moduleFilePath is empty for non-persistent modules. Such modules should disappear when IDE exits,
    // hence they should not be mentioned in `modules.xml`.
    if (moduleFilePath.isNotEmpty()) {
      val moduleElement = Element(JpsProjectLoader.MODULE_TAG)
      val url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, moduleFilePath)
      moduleElement.setAttribute(JpsProjectLoader.FILE_URL_ATTRIBUTE, url)
      // support for older builds
      moduleElement.setAttribute(JpsProjectLoader.FILE_PATH_ATTRIBUTE, moduleFilePath)

      groupPathString?.let {
        moduleElement.setAttribute(JpsProjectLoader.GROUP_ATTRIBUTE, it)
      }
      parentElement.addContent(moduleElement)
    }
  }
}

internal class ModulePathSaveItem(private val modulePath: ModulePath) : SaveItem() {
  override val groupPathString: String?
    get() = modulePath.group

  override val moduleFilePath: String
    get() = modulePath.path

  override val moduleName: String
    get() = modulePath.moduleName
}