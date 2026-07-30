// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components

import com.intellij.internal.inspector.ComponentPropertiesCollector
import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorCustomComponentChildProvider
import com.intellij.internal.inspector.UiInspectorUtil
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ComponentTreeJsonExporter {
  @OptIn(ExperimentalSerializationApi::class)
  private val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  @JvmStatic
  fun export(root: Any?): String {
    if (root !is HierarchyTree.ComponentNode) return ""

    return json.encodeToString(buildNode(root))
  }

  private fun buildNode(node: HierarchyTree.ComponentNode): JsonObject = buildJsonObject {
    put("class", getNodeName(node))

    node.component?.let { component ->
      put("size", "${component.width}x${component.height}")
    }

    put("property", buildJsonArray {
      val properties = getNodeProperties(node)
      if (properties.isNotEmpty()) {
        add(buildJsonObject {
          for (property in properties) {
            put(property.propertyName, getPropertyValue(property))
          }
        })
      }
    })

    put("children", buildJsonArray {
      for (i in 0 until node.childCount) {
        add(buildNode(node.getChildAt(i) as HierarchyTree.ComponentNode))
      }
    })
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
