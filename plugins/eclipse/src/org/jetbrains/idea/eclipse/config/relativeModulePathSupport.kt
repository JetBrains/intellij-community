// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config

import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsFileContentReader
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsModuleListSerializer
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
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
    val url = "${storageRoot.url}/${relativePath.removePrefix("/")}"
    val file = VirtualFileManager.getInstance().findFileByUrl(url) ?: return null
    return JarFileSystem.getInstance().getJarRootForLocalFile(file)?.url ?: file.url
  }
}

class ModulePathShortener(private val storage: EntityStorage) {
  private val contentRootsToModule by lazy {
    storage.entities(ContentRootEntity::class.java).associateBy({ it.url.url }, { it.module.name })
  }

  fun shortenPath(file: VirtualFile): String? {
    var current: VirtualFile? = file
    val map = contentRootsToModule
    while (current != null) {
      val moduleName = map[current.url]
      if (moduleName != null) {
        return "/$moduleName${file.url.substring(current.url.length)}"
      }
      current = current.parent
    }
    return null
  }

  fun isUnderContentRoots(file: VirtualFile): Boolean {
    val map = contentRootsToModule
    return generateSequence(file, {it.parent}).any { it.url in map }
  }
}
