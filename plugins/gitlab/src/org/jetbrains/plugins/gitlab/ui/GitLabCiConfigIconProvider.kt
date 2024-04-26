// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.jetbrains.plugins.gitlab.GitlabIcons
import javax.swing.Icon

class GitLabCiConfigIconProvider : FileIconProvider {
  private val PARSE_DELAY = 250L
  private val GITLAB_CI_FILE_MASK = Regex(""".*\.gitlab-ci(\..*)?\.(yaml|yml)""")
  private val GITLAB_SCHEMAS = setOf("ci.json")
  private val yamlExtensions = setOf("yml", "yaml")

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {

    val extension = file.extension ?: return null

    if (!yamlExtensions.contains(extension.lowercase())) return null

    if (isGitlabCiFile(file)) {
      return GitlabIcons.GitLabLogo
    } else if (project != null) {
      val schemaFiles = project.service<JsonSchemaService>().getSchemaFilesForFile(file)
      if (schemaFiles.any { GITLAB_SCHEMAS.contains(it.name) }) {
        return GitlabIcons.GitLabLogo
      }
    }
    return null
  }

  private fun isGitlabCiFile(file: VirtualFile): Boolean {
    return GITLAB_CI_FILE_MASK.matches(StringUtil.newBombedCharSequence(file.name, PARSE_DELAY))
  }
}