// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsComponentLoader
import org.jetbrains.jps.model.serialization.JpsProjectConfigurationLoading
import org.jetbrains.jps.model.serialization.JpsProjectConfigurationLoading.createProjectMacroExpander
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

/**
 * Loads data of module-level and project-level components directly from XML and iml configuration files.
 */
internal class ProjectDirectJpsFileContentReader(
  projectBaseDir: Path, 
  val externalConfigurationDirectory: Path?,
  private val pathVariables: Map<String, String>
) : JpsFileContentReader {
    
  private val projectMacroExpander = createProjectMacroExpander(pathVariables, projectBaseDir)
  private val moduleLoadersCache = ConcurrentHashMap<String, JpsComponentLoader>()
  val projectComponentLoader = JpsComponentLoader(projectMacroExpander, externalConfigurationDirectory, true)
  
  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    if (fileUrl.endsWith(".iml")) {
      //todo support external storage
      val loader = getModuleLoader(fileUrl)
      val rootElement = loader.loadRootElement(Path(JpsPathUtil.urlToPath(fileUrl)))
      if (componentName == "DeprecatedModuleOptionManager") {
        return DefaultImlNormalizer.createDeprecatedModuleOptionManager(rootElement)
      }
      return JDomSerializationUtil.findComponent(rootElement, componentName)
    }
    return loadProjectLevelComponent(fileUrl, componentName)
  }

  private fun loadProjectLevelComponent(fileUrl: String, componentName: String): Element? {
    val rootElement = projectComponentLoader.loadRootElement(Path(JpsPathUtil.urlToPath(fileUrl))) ?: return null
    if (rootElement.name == JDomSerializationUtil.COMPONENT_ELEMENT && JDomSerializationUtil.isComponent(componentName, rootElement)) {
      return rootElement
    }
    return JDomSerializationUtil.findComponent(rootElement, componentName)
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    if (fileUrl.endsWith(".iml")) {
      return getModuleLoader(fileUrl).macroExpander.expandMacroMap
    }
    return projectMacroExpander.expandMacroMap
  }

  private fun getModuleLoader(imlFileUrl: String): JpsComponentLoader {
    moduleLoadersCache[imlFileUrl]?.let {
      return it
    }
    val moduleFile = Path(JpsPathUtil.urlToPath(imlFileUrl))
    val macroExpander = JpsProjectConfigurationLoading.createModuleMacroExpander(pathVariables, moduleFile)
    val loader = JpsComponentLoader(macroExpander, null, true)
    moduleLoadersCache[imlFileUrl] = loader
    return loader 
  }
}
