// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.gitlab.icons.GitlabIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private val GITLAB_CI_FILE_MASK = Regex(""".*\.gitlab-ci(\..*)?\.(yaml|yml)""")

@ApiStatus.Internal
fun isGitlabCiFile(file: VirtualFile): Boolean {
  return GITLAB_CI_FILE_MASK.matches(file.name)
}

internal class GitlabCiIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (file.fileType.name != "YAML") return null

    return if (isGitlabCiFile(file)) GitlabIcons.GitLabLogo else null
  }
}