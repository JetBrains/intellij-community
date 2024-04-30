// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gitlab.GitlabIcons
import javax.swing.Icon

private val GITLAB_CI_FILE_MASK = Regex(""".*\.gitlab-ci(\..*)?\.(yaml|yml)""")

internal class GitLabCiConfigIconProvider : FileIconProvider {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    val yamlFileType = FileTypeManager.getInstance().findFileTypeByName("YAML") ?: return null
    if (file.fileType != yamlFileType) return null

    if (GITLAB_CI_FILE_MASK.matches(file.name)) {
      return GitlabIcons.GitLabLogo
    }

    return null
  }
}