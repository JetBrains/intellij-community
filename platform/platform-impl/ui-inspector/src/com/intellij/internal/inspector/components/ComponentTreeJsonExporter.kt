// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components

import com.intellij.internal.inspector.ComponentPropertiesCollector
import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorCustomComponentChildProvider
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ComponentTreeJsonExporter {
  @JvmStatic
  fun export(root: Any?): String {
    if (root !is HierarchyTree.ComponentNode) return ""

    return buildString {
      appendNode(this, root, 0)
    }
  }

  private fun appendNode(builder: StringBuilder, node: HierarchyTree.ComponentNode, depth: Int) {
    builder.append("  ".repeat(depth)).append("{\n")
    appendJsonProperty(builder, "class", getNodeName(node), depth + 1, true)

    node.component?.let { component ->
      appendJsonProperty(builder, "size", "${component.width}x${component.height}", depth + 1, true)
    }

    builder.append("  ".repeat(depth + 1)).append("\"property\": [")
    appendPropertiesJson(builder, getNodeProperties(node), depth + 2)
    builder.append("],\n")

    builder.append("  ".repeat(depth + 1)).append("\"children\": [")
    val childCount = node.childCount
    if (childCount > 0) builder.append('\n')
    for (i in 0 until childCount) {
      appendNode(builder, node.getChildAt(i) as HierarchyTree.ComponentNode, depth + 2)
      if (i + 1 < childCount) builder.append(',')
      builder.append('\n')
    }
    if (childCount > 0) builder.append("  ".repeat(depth + 1))
    builder.append("]\n")
    builder.append("  ".repeat(depth)).append('}')
  }

  private fun appendPropertiesJson(builder: StringBuilder, properties: List<PropertyBean>, depth: Int) {
    if (properties.isEmpty()) return

    builder.append('\n').append("  ".repeat(depth)).append('{').append('\n')
    properties.forEachIndexed { index, property ->
      appendJsonProperty(builder, property.propertyName, getPropertyValue(property), depth + 1, index + 1 < properties.size)
    }
    builder.append("  ".repeat(depth)).append('}').append('\n').append("  ".repeat(depth - 1))
  }

  private fun appendJsonProperty(builder: StringBuilder, name: String, value: String, depth: Int, appendComma: Boolean) {
    builder.append("  ".repeat(depth))
    appendJsonString(builder, name)
    builder.append(": ")
    appendJsonString(builder, value)
    if (appendComma) builder.append(',')
    builder.append('\n')
  }

  private fun appendJsonString(builder: StringBuilder, value: String) {
    builder.append('"')
    StringUtil.escapeStringCharacters(value.length, value, builder)
    builder.append('"')
  }

  private fun getNodeName(node: HierarchyTree.ComponentNode): String {
    return node.component?.let(UiInspectorUtil::getComponentName) ?: node.toString()
  }

  private fun getNodeProperties(node: HierarchyTree.ComponentNode): List<PropertyBean> {
    node.component?.let { component ->
      return ComponentPropertiesCollector.collect(component)
    }

    node.accessible?.let { accessible ->
      return ComponentPropertiesCollector.collect(accessible)
    }

    return when (val userObject = node.userObject) {
      is UiInspectorCustomComponentChildProvider -> {
        buildList {
          userObject.getObjectForProperties()?.let { propertiesHolder ->
            addAll(ComponentPropertiesCollector.collect(propertiesHolder, userObject.getPropertiesMethodList()))
          }
          addAll(userObject.getUiInspectorContext())
        }
      }
      is List<*> -> userObject.filterIsInstance<PropertyBean>()
      else -> emptyList()
    }
  }

  private fun getPropertyValue(property: PropertyBean): String {
    return property.propertyValue?.let(ValueCellRenderer::getToStringValue) ?: "-"
  }
}
