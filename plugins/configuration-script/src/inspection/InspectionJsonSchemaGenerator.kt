package com.intellij.configurationScript.inspection

import com.intellij.configurationScript.SchemaGenerator
import org.jetbrains.io.JsonObjectBuilder

internal class InspectionJsonSchemaGenerator : SchemaGenerator {
  override fun generate(rootBuilder: JsonObjectBuilder) {
    rootBuilder.map(ExternallyConfigurableProjectInspectionProfileManager.KEY) {
      "type" to "object"
      "description" to "The inspections"
      map("properties") {
        map("disableAll") {
          "type" to "boolean"
          "description" to "Whether to disable all inspections by default"
          "default" to false
        }
        map("enable") {
          "type" to "array"
          "description" to "The enabled inspections"
          map("items") {
            "type" to "string"
          }
        }
        map("disable") {
          "type" to "array"
          "description" to "The disabled inspections"
          map("items") {
            "type" to "string"
          }
        }
      }
      "additionalProperties" to false
    }
  }
}