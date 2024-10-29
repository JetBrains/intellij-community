// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.metaInformation

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.idea.devkit.DevKitBundle

internal class MetaInformationJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    return listOf(MetaInformationJsonSchemaProvider(project))
  }

  class MetaInformationJsonSchemaProvider(val project: Project) : JsonSchemaFileProvider {
    override fun getName(): String = DevKitBundle.message("inspections.meta.information.json.schema.display.name")

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    override fun isAvailable(file: VirtualFile): Boolean {
      return isMetaInformationFile(file, project)
    }

    override fun getSchemaFile(): VirtualFile? {
      return JsonSchemaProviderFactory.getResourceFile(MetaInformationJsonSchemaProviderFactory::class.java, "/schemas/meta-information-schema.json")
    }
  }
}