// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.update.UpdateOptionsDialogProvider
import com.intellij.openapi.vcs.update.UpdateOrStatusOptionsDialog
import git4idea.GitVcs

internal class GitUpdateOptionsDialogProvider : UpdateOptionsDialogProvider {
  override fun create(
    project: Project,
    title: @NlsSafe String,
    envToConfMap: LinkedHashMap<Configurable, AbstractVcs>,
  ): UpdateOrStatusOptionsDialog? {
    val vcs = envToConfMap.values.singleOrNull()
    if (vcs !is GitVcs) return null

    return GitUpdateOptionsDialog(project, title, envToConfMap)
  }
}