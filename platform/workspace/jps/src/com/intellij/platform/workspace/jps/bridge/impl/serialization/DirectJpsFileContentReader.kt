// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.openapi.components.ExpandMacroToPathMap
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsLoaderBase
import org.jetbrains.jps.model.serialization.JpsMacroExpander
import org.jetbrains.jps.util.JpsPathUtil
import kotlin.io.path.Path

internal class DirectJpsFileContentReader(private val macroExpander: JpsMacroExpander) : JpsLoaderBase(macroExpander), JpsFileContentReader {
  override fun loadComponent(fileUrl: String, componentName: String, customModuleFilePath: String?): Element? {
    val rootElement = loadRootElement(Path(JpsPathUtil.urlToPath(fileUrl))) ?: return null
    return JDomSerializationUtil.findComponent(rootElement, componentName)
  }

  override fun getExpandMacroMap(fileUrl: String): ExpandMacroToPathMap {
    return macroExpander.expandMacroMap
  }
}
