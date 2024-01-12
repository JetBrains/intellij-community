// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.jetbrains.yaml.YAMLFileType
import javax.swing.Icon

internal class GithubYamlIconProvider : FileIconProvider {
  private val GITHUB_SCHEMA_NAMES: Set<String> = setOf("github-workflow", "github-action")

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {

    if (project == null || !FileTypeRegistry.getInstance().isFileOfType(file, YAMLFileType.YML)) {
      return null
    }

    val schemaFiles = project.service<JsonSchemaService>().getSchemaFilesForFile(file)
    if (schemaFiles.any { GITHUB_SCHEMA_NAMES.contains(it.nameWithoutExtension) }) {
      return AllIcons.Vcs.Vendors.Github
    }

    return null
  }
}
