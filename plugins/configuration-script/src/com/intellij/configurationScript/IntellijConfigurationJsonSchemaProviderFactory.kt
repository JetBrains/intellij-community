// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationScript

import com.intellij.json.JsonFileType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.SystemProperties
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import org.intellij.lang.annotations.Language
import java.nio.charset.StandardCharsets

internal val LOG = logger<IntellijConfigurationJsonSchemaProviderFactory>()

internal class IntellijConfigurationJsonSchemaProviderFactory : JsonSchemaProviderFactory, JsonSchemaFileProvider {
  private val schemeFile: VirtualFile by lazy { generateConfigurationSchema() }

  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    return listOf(this)
  }

  override fun isAvailable(file: VirtualFile): Boolean {
    val nameSequence = file.nameSequence
    return (nameSequence.endsWith(".yaml") || nameSequence.endsWith(".yml")) && StringUtil.equals(file.parent?.nameSequence, Project.DIRECTORY_STORE_FOLDER)
  }

  override fun getName() = "IntelliJ Configuration"

  override fun getSchemaFile(): VirtualFile? {
    if (SystemProperties.getBooleanProperty("configuration.schema.cache", true)) {
      return schemeFile
    }
    else {
      // simplify development - ability to apply changes on hotswap
      return generateConfigurationSchema()
    }
  }

  override fun getSchemaType() = SchemaType.embeddedSchema

  override fun getSchemaVersion() = JsonSchemaVersion.SCHEMA_7
}

private fun generateConfigurationSchema(): LightVirtualFile {
  // fake vars to avoid escaping
  @Suppress("JsonStandardCompliance")
  val schema = "\$schema"
  @Suppress("JsonStandardCompliance")
  val id = "\$id"
  @Suppress("JsonStandardCompliance")
  val ref = "\$ref"

  val runConfigurationsProperties = StringBuilder()
  val definitions = StringBuilder()
  buildRunConfigurationTypeSchema(runConfigurationsProperties, definitions)

  @Language("JSON")
  val data = """
  {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "$id": "https://jetbrains.com/intellij-configuration.schema.json",
    "title": "IntelliJ Configuration",
    "description": "IntelliJ Configuration File to configure IDE behavior, run configurations and so on",
    "type": "object",
    "definitions": {
      $definitions
      "RunConfigurations": {
        "properties": {
          $runConfigurationsProperties
        },
        "additionalProperties": false
      }
    },
    "properties": {
      "${Keys.runConfigurations}": {
        "description": "The run configurations",
        "type": "object",
        "$ref": "#/definitions/RunConfigurations"
      }
    },
    "additionalProperties": false
  }
  """
  return LightVirtualFile("scheme.json", JsonFileType.INSTANCE, data.trimIndent(), StandardCharsets.UTF_8, 0)
}

@Suppress("JsonStandardCompliance")
object Keys {
  const val runConfigurations = "runConfigurations"
}