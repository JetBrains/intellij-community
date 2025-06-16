// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.github.git.share

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ShareProjectActionProvider
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle

internal class GithubShareActionProvider : ShareProjectActionProvider {
  override val hostServiceName: @Nls String = GithubBundle.message("settings.configurable.display.name")
  override val action: AnAction
    get() = ActionManager.getInstance().getAction("Github.Share")

  override fun isApplicableForRoot(project: Project, root: VirtualFile): Boolean =
    GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) != null
}