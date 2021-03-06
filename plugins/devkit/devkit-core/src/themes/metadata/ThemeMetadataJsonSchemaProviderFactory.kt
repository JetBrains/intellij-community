// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes.metadata

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import java.util.*

class ThemeMetadataJsonSchemaProviderFactory : JsonSchemaProviderFactory {

  override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
    return Collections.singletonList(object : JsonSchemaFileProvider {
      override fun getName(): String = DevKitBundle.message("theme.metadata.json.display.name")

      override fun isAvailable(file: VirtualFile): Boolean = file.nameSequence.endsWith(DOT_EXTENSION)

      override fun getSchemaFile(): VirtualFile? = VfsUtil.findFileByURL(javaClass.getResource(THEME_METADATA_SCHEMA))

      override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
    })
  }

  companion object {
    @NonNls
    const val EXTENSION = "themeMetadata.json"

    @NonNls
    private const val DOT_EXTENSION = ".$EXTENSION"

    @NonNls
    private const val THEME_METADATA_SCHEMA = "/schemes/themeMetadata.schema.json"
  }
}