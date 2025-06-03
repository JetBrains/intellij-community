// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.components.impl.stores

import com.intellij.application.options.PathMacrosCollector
import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.components.CompositePathMacroFilter
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.SmartList
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

@ApiStatus.Internal
object ComponentStorageUtil {
  const val COMPONENT: String = "component"
  const val NAME: String = "name"
  const val DEFAULT_EXT: String = ".xml"

  @JvmStatic
  fun loadComponents(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?): Map<String, Element> {
    pathMacroSubstitutor?.expandPaths(rootElement)

    var children = rootElement.getChildren(COMPONENT)
    if (children.isEmpty() &&
        rootElement.name == COMPONENT &&
        rootElement.getAttributeValue(NAME) != null) {
      // must be modifiable
      children = SmartList(rootElement)
    }

    val map = TreeMap<String, Element>()

    var filter: CompositePathMacroFilter? = null
    val iterator = children.iterator()
    while (iterator.hasNext()) {
      val element = iterator.next()
      val name = getComponentNameIfValid(element)
      if (name == null || (element.attributes.size <= 1 && element.content.isEmpty())) {
        continue
      }

      // so, PathMacroFilter can easily find the component name (null parent)
      iterator.remove()

      if (pathMacroSubstitutor is TrackingPathMacroSubstitutor && !isKotlinSerializable(element)) {
        if (filter == null) {
          filter = CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.extensionList)
        }
        pathMacroSubstitutor.addUnknownMacros(name, PathMacrosCollector.getMacroNames(element, filter, PathMacrosImpl.getInstanceEx()))
      }

      // remove only after "getMacroNames" - some PathMacroFilter requires an element name attribute
      element.removeAttribute(NAME)

      map.put(name, element)
    }

    return map
  }

  @JvmStatic
  fun getComponentNameIfValid(element: Element): String? {
    val name = element.getAttributeValue(NAME)
    if (!name.isNullOrEmpty()) {
      return name
    }
    else {
      logger<ComponentStorageUtil>().warn("No name attribute for component in ${JDOMUtil.writeElement(element)}")
      return null
    }
  }

  @JvmStatic
  @Throws(IOException::class)
  fun loadTextContent(file: Path): String {
    val data = Files.readAllBytes(file)
    val offset = CharsetToolkit.getBOMLength(data, StandardCharsets.UTF_8)
    return String(data, offset, data.size - offset, StandardCharsets.UTF_8)
  }
}

private fun isKotlinSerializable(element: Element): Boolean {
  return !element.hasAttributes() && element.content.size == 1 && element.content[0] is Text
}