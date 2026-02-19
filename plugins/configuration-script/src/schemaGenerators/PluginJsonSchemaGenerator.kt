package com.intellij.configurationScript.schemaGenerators

import com.intellij.configurationScript.SchemaGenerator
import com.intellij.openapi.updateSettings.impl.PluginsConfiguration
import org.jetbrains.io.JsonObjectBuilder

internal class PluginJsonSchemaGenerator : SchemaGenerator {
  companion object {
    const val plugins = "plugins"
  }

  override fun generate(rootBuilder: JsonObjectBuilder) {
    rootBuilder.map(plugins) {
      "type" to "object"
      "description" to "The plugins"
      map("properties") {
        buildJsonSchema(
          PluginsConfiguration(), this, subObjectSchemaGenerator = null)
      }
      "additionalProperties" to false
    }
  }
}