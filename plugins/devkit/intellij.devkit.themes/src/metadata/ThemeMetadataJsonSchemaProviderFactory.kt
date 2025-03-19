// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes.metadata

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.themes.DevKitThemesBundle
import java.util.*

@NonNls
internal const val THEME_METADATA_JSON_EXTENSION = "themeMetadata.json"

internal class ThemeMetadataJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {

  override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
    return Collections.singletonList(object : JsonSchemaFileProvider {
      override fun getName(): String = DevKitThemesBundle.message("theme.metadata.json.display.name")

      override fun isAvailable(file: VirtualFile): Boolean = file.nameSequence.endsWith(DOT_EXTENSION)

      override fun getSchemaFile(): VirtualFile? = VfsUtil.findFileByURL(javaClass.getResource(THEME_METADATA_SCHEMA)!!)

      override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
    })
  }

  @NonNls
  private val DOT_EXTENSION = ".$THEME_METADATA_JSON_EXTENSION"

  @NonNls
  private val THEME_METADATA_SCHEMA = "/schemes/themeMetadata.schema.json"

}