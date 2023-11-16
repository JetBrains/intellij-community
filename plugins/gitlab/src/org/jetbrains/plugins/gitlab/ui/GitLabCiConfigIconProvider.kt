// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.jetbrains.plugins.gitlab.GitlabIcons
import org.jetbrains.yaml.YAMLFileType
import javax.swing.Icon

class GitLabCiConfigIconProvider : FileIconProvider {

  private val GITLAB_SCHEMAS = setOf("ci.json")

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {

    if (project == null || !FileTypeRegistry.getInstance().isFileOfType(file, YAMLFileType.YML)) {
      return null
    }

    val schemaFiles = project.service<JsonSchemaService>().getSchemaFilesForFile(file)
    if (schemaFiles.any { GITLAB_SCHEMAS.contains(it.name) }) {
      return GitlabIcons.GitLabLogo
    }

    return null
  }
}