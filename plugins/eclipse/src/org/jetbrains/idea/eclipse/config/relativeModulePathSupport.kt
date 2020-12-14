// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsModuleListSerializer
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.util.JpsPathUtil

class ModuleRelativePathResolver(private val moduleListSerializer: JpsModuleListSerializer?,
                                 private val reader: JpsFileContentReader,
                                 private val virtualFileManager: VirtualFileUrlManager) {
  private val moduleFileUrls by lazy {
    (moduleListSerializer?.loadFileList(reader, virtualFileManager) ?: emptyList()).associateBy(
      { getModuleNameByFilePath(JpsPathUtil.urlToPath(it.first.url)) },
      { it.first }
    )
  }

  fun resolve(moduleName: String, relativePath: String?): String? {
    val moduleFile = moduleFileUrls[moduleName] ?: return null
    val component = reader.loadComponent(moduleFile.url, "DeprecatedModuleOptionManager", null)
    val baseDir = component?.getChildren("option")
      ?.firstOrNull { it.getAttributeValue("key") == JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE }
      ?.getAttributeValue("value")
    val storageRoot = getStorageRoot(moduleFile, baseDir, virtualFileManager)
    if (relativePath.isNullOrEmpty()) return storageRoot.url
    return "${storageRoot.url}/${relativePath.removePrefix("/")}"
  }
}

class ModulePathShortener(private val storage: WorkspaceEntityStorage) {
  private val contentRootsToModule by lazy {
    storage.entities(ContentRootEntity::class.java).associateBy({ it.url }, { it.module.name })
  }

  fun shortenPath(url: VirtualFileUrl): String? {
    var current: VirtualFileUrl? = url
    val map = contentRootsToModule
    while (current != null) {
      val moduleName = map[current]
      if (moduleName != null) {
        return "/$moduleName${url.url.substring(current.url.length)}"
      }
      current = current.parent
    }
    return null
  }

  fun isUnderContentRoots(url: VirtualFileUrl): Boolean {
    val map = contentRootsToModule
    return generateSequence(url, {it.parent}).any { it in map }
  }
}
