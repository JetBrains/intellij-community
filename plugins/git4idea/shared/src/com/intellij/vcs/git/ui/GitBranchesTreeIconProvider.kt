// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.CheckboxIcon
import com.intellij.vcs.git.repo.GitRepositoryColorsHolder
import git4idea.GitReference
import git4idea.GitTag
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
object GitBranchesTreeIconProvider {
  fun forRef(gitReference: GitReference, current: Boolean, favorite: Boolean, selected: Boolean, favoriteToggleOnClick: Boolean = false): Icon = when {
    selected && !favorite && favoriteToggleOnClick -> AllIcons.Nodes.NotFavoriteOnHover
    current && favorite -> DvcsImplIcons.CurrentBranchFavoriteLabel
    current -> DvcsImplIcons.CurrentBranchLabel
    favorite -> AllIcons.Nodes.Favorite
    gitReference is GitTag -> DvcsImplIcons.BranchLabel
    else -> AllIcons.Vcs.BranchNode
  }

  fun forRepository(project: Project, repositoryId: RepositoryId): Icon {
    val color = GitRepositoryColorsHolder.getInstance(project).getColor(repositoryId)
    if (color == null) return GitRepoIconProvider.getIcon(project, repositoryId)
    return CheckboxIcon.createAndScale(color)
  }

  fun forGroup(): Icon = PlatformIcons.FOLDER_ICON
}