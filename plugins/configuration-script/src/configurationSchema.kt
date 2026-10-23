// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationScript

import com.intellij.configurationScript.inspection.InspectionJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.ComponentStateJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.PluginJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.RunConfigurationJsonSchemaGenerator
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.io.JsonObjectBuilder
import org.jetbrains.io.json

internal val LOG = Logger.getInstance("#com.intellij.configurationScript")

/** Builds the JSON schema of the configuration file. The build is slow, call it on demand. */
@ApiStatus.Internal
fun generateConfigurationSchema(): CharSequence {
  return doGenerateConfigurationSchema(listOf(
    PluginJsonSchemaGenerator(),
    RunConfigurationJsonSchemaGenerator(),
    ComponentStateJsonSchemaGenerator(),
    InspectionJsonSchemaGenerator())
  )
}

internal interface SchemaGenerator {
  val definitionNodeKey: CharSequence?
    get() = null

  fun generate(rootBuilder: JsonObjectBuilder)

  fun generateDefinitions(): CharSequence = ""
}

internal fun doGenerateConfigurationSchema(generators: List<SchemaGenerator>): CharSequence {
  val stringBuilder = StringBuilder()
  stringBuilder.json {
    "\$schema" to "http://json-schema.org/draft-07/schema#"
    "\$id" to "https://jetbrains.com/intellij-configuration.schema.json"
    "title" to "IntelliJ Configuration"
    "description" to "IntelliJ Configuration to configure IDE behavior, run configurations and so on"

    "type" to "object"

    map("properties") {
      for (generator in generators) {
        generator.generate(this)
      }
    }

    for (generator in generators) {
      val definitionNodeKey = generator.definitionNodeKey
      if (definitionNodeKey != null) {
        val data = generator.generateDefinitions()
        if (data.isNotEmpty()) {
          rawMap(definitionNodeKey) {
            it.append(data)
          }
        }
      }
    }
    "additionalProperties" to false
  }
  return stringBuilder
}

@Suppress("JsonStandardCompliance")
internal object Keys {
  const val runConfigurations = "runConfigurations"
  const val templates = "templates"
}