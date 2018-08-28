// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationScript

import com.intellij.json.JsonFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
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

private fun projectManagerListener(file: MyLightVirtualFile): ProjectManagerListener {
  return object : ProjectManagerListener {
    override fun projectClosed(project: Project) {
      file.clearUserData()
    }
  }
}

internal class IntellijConfigurationJsonSchemaProviderFactory : JsonSchemaProviderFactory, JsonSchemaFileProvider {
  private val schemeFile: VirtualFile by lazy {
    val file = generateConfigurationSchemaFile()
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, projectManagerListener(file))
    file
  }

  override fun getProviders(project: Project) = listOf(this)

  override fun isAvailable(file: VirtualFile): Boolean {
    val nameSequence = file.nameSequence
    return StringUtil.equals(nameSequence, IDE_FILE) || StringUtil.equals(nameSequence, IDE_FILE_VARIANT_2)
  }

  override fun getName() = "IntelliJ Configuration"

  override fun getSchemaFile(): VirtualFile? {
    if (SystemProperties.getBooleanProperty("configuration.schema.cache", true)) {
      return schemeFile
    }
    else {
      // simplify development - ability to apply changes on hotswap
      return generateConfigurationSchemaFile()
    }
  }

  override fun getSchemaType() = SchemaType.embeddedSchema

  override fun getSchemaVersion() = JsonSchemaVersion.SCHEMA_7
}

private fun generateConfigurationSchemaFile(): MyLightVirtualFile {
  return MyLightVirtualFile(generateConfigurationSchema())
}

private class MyLightVirtualFile(data: CharSequence) : LightVirtualFile("scheme.json", JsonFileType.INSTANCE, data, StandardCharsets.UTF_8, 0) {
  override public fun clearUserData() {
    super.clearUserData()
  }
}

internal fun generateConfigurationSchema(): CharSequence {
  // fake vars to avoid escaping
  @Suppress("JsonStandardCompliance")
  val schema = "\$schema"
  @Suppress("JsonStandardCompliance")
  val id = "\$id"
  @Suppress("JsonStandardCompliance")
  val ref = "\$ref"

  val defBuilder = StringBuilder()
  val definitions = JsonObjectBuilder(defBuilder)

  val rcProperties = JsonObjectBuilder(StringBuilder())
  RunConfigurationJsonSchemaGenerator(definitions).generate(rcProperties)

  definitions.map("RunConfigurations") {
    rawBuilder("properties", rcProperties)
    "additionalProperties" to false
  }

  @Suppress("UnnecessaryVariable")
  @Language("JSON")
  val data = """
  {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "$id": "https://jetbrains.com/intellij-configuration.schema.json",
    "title": "IntelliJ Configuration",
    "description": "IntelliJ Configuration to configure IDE behavior, run configurations and so on",
    "type": "object",
    "definitions": {
      $defBuilder
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
  return data
}

@Suppress("JsonStandardCompliance")
object Keys {
  const val runConfigurations = "runConfigurations"
}