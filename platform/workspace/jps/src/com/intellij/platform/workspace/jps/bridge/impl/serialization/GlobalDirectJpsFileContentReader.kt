// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsComponentLoader
import org.jetbrains.jps.model.serialization.JpsMacroExpander
import org.jetbrains.jps.util.JpsPathUtil
import kotlin.io.path.Path

/**
 * Loads global settings used in the workspace model directly from XML configuration files. 
 */
internal class GlobalDirectJpsFileContentReader(private val macroExpander: JpsMacroExpander) : JpsFileContentReader {
  private val componentLoader = JpsComponentLoader(macroExpander, null)
  
  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    return componentLoader.loadComponent(Path(JpsPathUtil.urlToPath(fileUrl)), componentName)
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    return macroExpander.expandMacroMap
  }
}
