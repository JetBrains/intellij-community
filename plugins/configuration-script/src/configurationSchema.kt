// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationScript

import com.intellij.configurationScript.inspection.InspectionJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.ComponentStateJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.PluginJsonSchemaGenerator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.io.JsonObjectBuilder
import org.jetbrains.io.json

internal val LOG = Logger.getInstance("#com.intellij.configurationScript")

/** Builds the JSON schema of the configuration file. The build is slow, call it on demand. */
@ApiStatus.Internal
fun generateConfigurationSchema(): CharSequence {
  return doGenerateConfigurationSchema(
    buildList {
      add(PluginJsonSchemaGenerator())
      SchemaGeneratorProvider.EP_NAME.extensionList.mapTo(this) { it.createGenerator() }
      add(ComponentStateJsonSchemaGenerator())
      add(InspectionJsonSchemaGenerator())
    }
  )
}

/** Creates a [SchemaGenerator] for a part of the configuration file schema that needs an optional dependency. */
@ApiStatus.Internal
interface SchemaGeneratorProvider {
  companion object {
    val EP_NAME: ExtensionPointName<SchemaGeneratorProvider> = ExtensionPointName("com.intellij.configurationScript.schemaGeneratorProvider")
  }

  /** Returns a new generator for each schema build, because a generator keeps the state of the build. */
  fun createGenerator(): SchemaGenerator
}

@ApiStatus.Internal
interface SchemaGenerator {
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
