// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.ide.plugins.PluginNode

/**
 * Object from Search Service for getting compatible updates for IDE.
 * [externalUpdateId] update ID from Plugin Repository database.
 * [externalPluginId] plugin ID from Plugin Repository database.
 */
data class IdeCompatibleUpdate(
  @get:JsonProperty("id")
  val externalUpdateId: String = "",
  @get:JsonProperty("pluginId")
  val externalPluginId: String = "",
  @get:JsonProperty("pluginXmlId")
  val pluginId: String = "",
  val version: String = ""
)

/**
 * Plugin Repository object for storing information about plugin updates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IntellijUpdateMetadata(
  @get:JsonProperty("xmlId")
  val id: String = "",
  val name: String = "",
  val description: String = "",
  val tags: List<String> = emptyList(),
  val vendor: String = "",
  val version: String = "",
  val notes: String = "",
  val dependencies: Set<String> = emptySet(),
  val since: String? = null,
  val until: String? = null,
  val productCode: String? = null
) {
  fun toPluginNode(): PluginNode {
    val pluginNode = PluginNode()
    pluginNode.setId(id)
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
