// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
@file:Internal

package com.intellij.openapi.components.impl.stores

import com.intellij.application.options.PathMacrosCollector
import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.components.CompositePathMacroFilter
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.SmartList
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

fun loadComponents(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?): Map<String, Element> {
  pathMacroSubstitutor?.expandPaths(rootElement)

  var children = rootElement.getChildren(ComponentStorageUtil.COMPONENT)
  if (children.isEmpty() &&
      rootElement.name == ComponentStorageUtil.COMPONENT &&
      rootElement.getAttributeValue(ComponentStorageUtil.NAME) != null) {
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

    // so, PathMacroFilter can easily find component name (null parent)
    iterator.remove()

    if (pathMacroSubstitutor is TrackingPathMacroSubstitutor && !isKotlinSerializable(element)) {
      if (filter == null) {
        filter = CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.extensionList)
      }
      pathMacroSubstitutor.addUnknownMacros(name, PathMacrosCollector.getMacroNames(element, filter, PathMacrosImpl.getInstanceEx()))
    }

    // remove only after "getMacroNames" - some PathMacroFilter requires element name attribute
    element.removeAttribute(ComponentStorageUtil.NAME)

    map.put(name, element)
  }

  return map
}

object ComponentStorageUtil {
  const val COMPONENT: String = "component"
  const val NAME: String = "name"
  const val DEFAULT_EXT: String = ".xml"
}

private fun isKotlinSerializable(element: Element): Boolean {
  if (element.hasAttributes()) {
    return false
  }
  val content = element.content
  return content.size == 1 && content.get(0) is Text
}

fun getComponentNameIfValid(element: Element): String? {
  val name = element.getAttributeValue(ComponentStorageUtil.NAME)
  if (!name.isNullOrEmpty()) {
    return name
  }

  logger<ComponentStorageUtil>().warn("No name attribute for component in ${JDOMUtil.writeElement(element)}")
  return null
}
