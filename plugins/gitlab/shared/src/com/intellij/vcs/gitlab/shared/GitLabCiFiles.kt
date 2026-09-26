// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.gitlab.shared

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

private val GITLAB_CI_FILE_MASK = Regex(""".*\.gitlab-ci(\..*)?\.(yaml|yml)""")

@ApiStatus.Internal
fun isGitlabCiFile(file: VirtualFile): Boolean {
  return GITLAB_CI_FILE_MASK.matches(file.name)
}
