// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.documentation

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.idea.devkit.DevKitBundle

internal class DescriptorDocumentationYamlSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    return listOf(DescriptorDocumentationYamlSchemaProvider(project))
  }

  class DescriptorDocumentationYamlSchemaProvider(val project: Project) : JsonSchemaFileProvider {
    override fun getName(): String = DevKitBundle.message("descriptor.documentation.yaml.schema.display.name")

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema

    override fun isAvailable(file: VirtualFile): Boolean {
      return IntelliJProjectUtil.isIntelliJPlatformProject(project) && isDescriptorDocumentationFile(file)
    }

    private fun isDescriptorDocumentationFile(file: VirtualFile): Boolean {
      return file.extension == "yaml" && file.parent?.path?.endsWith("devkit-core/resources/documentation") == true
    }

    override fun getSchemaFile(): VirtualFile? {
      return JsonSchemaProviderFactory.getResourceFile(
        DescriptorDocumentationYamlSchemaProviderFactory::class.java, "/schemas/descriptor-documentation-schema.json"
      )
    }
  }
}
