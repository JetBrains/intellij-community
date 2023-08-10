// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.workspace.jps.JpsProjectConfigLocation
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.platform.workspace.jps.serialization.impl.isExternalModuleFile
import org.jdom.Element
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

class CachingJpsFileContentReader(private val configLocation: JpsProjectConfigLocation) : JpsFileContentReader {
  private val projectPathMacroManager = ProjectPathMacroManager.createInstance(
    configLocation::projectFilePath,
    { JpsPathUtil.urlToPath(configLocation.baseDirectoryUrlString) },
    null
  )
  private val fileContentCache = ConcurrentHashMap<String, Map<String, Element>>()

  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val content = fileContentCache.computeIfAbsent(fileUrl + customModuleFilePath) {
      loadComponents(fileUrl, customModuleFilePath)
    }
    return content[componentName]
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    return getMacroManager(fileUrl, null).expandMacroMap
  }

  private fun loadComponents(fileUrl: String, customModuleFilePath: String?): Map<String, Element> {
    val macroManager = getMacroManager(fileUrl, customModuleFilePath)

    val file = Paths.get(JpsPathUtil.urlToPath(fileUrl))
    return if (Files.isRegularFile(file)) loadStorageFile(file, macroManager) else emptyMap()
  }

  private fun getMacroManager(fileUrl: String,
                              customModuleFilePath: String?): PathMacroManager {
    val path = JpsPathUtil.urlToPath(fileUrl)
    return if (FileUtil.extensionEquals(fileUrl, "iml") || isExternalModuleFile(path)) {
      ModulePathMacroManager.createInstance(configLocation::projectFilePath) { customModuleFilePath ?: path }
    }
    else {
      projectPathMacroManager
    }
  }

  private fun loadStorageFile(xmlFile: Path, pathMacroManager: PathMacroManager): Map<String, Element> {
    val rootElement = JDOMUtil.load(xmlFile)
    if (Strings.endsWith(xmlFile.toString(), ".iml")) {
      val optionElement = Element("component").setAttribute("name", "DeprecatedModuleOptionManager")
      val iterator = rootElement.attributes.iterator()
      for (attribute in iterator) {
        if (attribute.name != "version") {
          iterator.remove()
          optionElement.addContent(Element("option").setAttribute("key", attribute.name).setAttribute("value", attribute.value))
        }
      }
      rootElement.addContent(optionElement)
    }
    return FileStorageCoreUtil.load(rootElement, pathMacroManager)
  }
}