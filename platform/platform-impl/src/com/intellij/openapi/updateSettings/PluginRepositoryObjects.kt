// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.intellij.ide.plugins.PluginNode

/**
 * Object from Search Service for getting compatible updates for IDE.
 * @param id        - update ID from Plugin Repository database.
 * @param pluginId  - plugin ID from Plugin Repository database.
 */
data class IdeCompatibleUpdate(
  val id: String = "",
  val pluginId: String = "",
  val version: String = ""
)

/**
 * Plugin Repository object for storing information about plugin updates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IntellijUpdateMetadata(
  val xmlId: String = "",
  val name: String = "",
  val description: String = "",
  val tags: List<String> = emptyList(),
  val vendor: String = "",
  val version: String = "",
  val notes: String = "",
  val dependencies: Set<String> = emptySet(),
  val since: String = "",
  val until: String = "",
  val productCode: String? = null
) {
  fun toPluginNode(): PluginNode {
    val pluginNode = PluginNode()
    pluginNode.setId(xmlId)
    pluginNode.name = name
    pluginNode.description = description
    pluginNode.vendor = vendor
    pluginNode.tags = tags
    pluginNode.changeNotes = notes
    pluginNode.sinceBuild = since
    pluginNode.untilBuild = until
    pluginNode.productCode = productCode
    pluginNode.version = version
    for (dep in dependencies) pluginNode.addDepends(dep)
    return pluginNode
  }
}
