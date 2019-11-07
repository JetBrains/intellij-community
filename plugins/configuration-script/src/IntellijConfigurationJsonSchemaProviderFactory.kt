// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationScript

import com.intellij.configurationScript.inspection.InspectionJsonSchemaGenerator
import com.intellij.configurationScript.providers.PluginsConfiguration
import com.intellij.configurationScript.schemaGenerators.ComponentStateJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.RunConfigurationJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.buildJsonSchema
import com.intellij.json.JsonFileType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.SystemProperties
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import org.jetbrains.io.JsonObjectBuilder
import org.jetbrains.io.json

internal val LOG = logger<IntellijConfigurationJsonSchemaProviderFactory>()

private val PROVIDER_KEY = Key.create<List<JsonSchemaFileProvider>>("IntellijConfigurationJsonSchemaProvider")

internal class IntellijConfigurationJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
  private val schemeContent by lazy {
    generateConfigurationSchema()
  }

  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    var result = PROVIDER_KEY.get(project)
    if (result != null) {
      return result
    }

    // LightVirtualFile is not cached as regular files by FileManagerImpl.findViewProvider, but instead HARD_REFERENCE_TO_PSI is set to user data and this value references project and so,
    // LightVirtualFile cannot be cached per application, must be stored per project.
    // Yes, it is hack, but for now decided to not fix this issue on platform level.
    result = listOf(MyJsonSchemaFileProvider())
    return (project as UserDataHolderBase).putUserDataIfAbsent(PROVIDER_KEY, result)
  }

  inner class MyJsonSchemaFileProvider : JsonSchemaFileProvider, DumbAware {
    private val schemeFile = lazy {
      //do not pass schemeContent directory directly because the initialization for the content is very slow (500ms)
      //use the lazy initialized field schemeContent only on demand 
      object: LightVirtualFile("ij-scheme.json", JsonFileType.INSTANCE, "", Charsets.UTF_8, 0) {
        override fun getContent(): CharSequence = schemeContent
      }
    }

    override fun getName() = "IntelliJ Configuration"

    override fun getSchemaFile(): VirtualFile? {
      if (!SystemProperties.getBooleanProperty("configuration.schema.cache", true) && schemeFile.isInitialized()) {
        // simplify development - ability to apply changes on hotswap
        val newData = generateConfigurationSchema()
        val file = schemeFile.value
        if (!StringUtil.equals(file.content, newData)) {
          file.setContent(null, newData, true)
        }
      }
      return schemeFile.value
    }

    override fun getSchemaType() = SchemaType.embeddedSchema

    override fun getSchemaVersion() = JsonSchemaVersion.SCHEMA_7

    override fun isUserVisible() = false

    override fun isAvailable(file: VirtualFile) = isConfigurationFile(file)
  }
}

private fun generateConfigurationSchema(): CharSequence {
  return doGenerateConfigurationSchema(listOf(RunConfigurationJsonSchemaGenerator(), ComponentStateJsonSchemaGenerator(),
    InspectionJsonSchemaGenerator()))
}

internal interface SchemaGenerator {
  fun generate(rootBuilder: JsonObjectBuilder)
}

internal fun doGenerateConfigurationSchema(generators: List<SchemaGenerator>): CharSequence {
  val runConfigurationGenerator = generators.find { it is RunConfigurationJsonSchemaGenerator } as? RunConfigurationJsonSchemaGenerator
  val stringBuilder = StringBuilder()
  stringBuilder.json {
    "\$schema" to "http://json-schema.org/draft-07/schema#"
    "\$id" to "https://jetbrains.com/intellij-configuration.schema.json"
    "title" to "IntelliJ Configuration"
    "description" to "IntelliJ Configuration to configure IDE behavior, run configurations and so on"

    "type" to "object"

    if (runConfigurationGenerator != null) {
      rawMap(RunConfigurationJsonSchemaGenerator.definitionNodeKey) {
        it.append(runConfigurationGenerator.generate())
      }
    }

    map("properties") {
      map(Keys.plugins) {
        "type" to "object"
        "description" to "The plugins"
        map("properties") {
          buildJsonSchema(PluginsConfiguration(), this)
        }
      }

      generators.forEach { it.generate(this) }
    }
    "additionalProperties" to false
  }
  return stringBuilder
}

@Suppress("JsonStandardCompliance")
internal object Keys {
  const val runConfigurations = "runConfigurations"
  const val templates = "templates"

  const val plugins = "plugins"
}