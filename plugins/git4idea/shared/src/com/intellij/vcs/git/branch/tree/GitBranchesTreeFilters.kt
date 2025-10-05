// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.vcs.git.branch.popup.GitBranchesPopupSettingsToggleAction.Companion.isMultiRoot
import git4idea.config.GitVcsSettings

internal object GitBranchesTreeFilters {
  fun byActions(project: Project) = GitVcsSettings.getInstance(project).filterByActionInPopup()

  fun byRepositoryName(project: Project) = GitVcsSettings.getInstance(project).filterByRepositoryInPopup() && isMultiRoot(project)
}