// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface GitRepoIconProvider {
  fun getIcon(project: Project, repositoryId: RepositoryId): Icon

  companion object {
    internal val EP_NAME = ExtensionPointName<GitRepoIconProvider>("Git4Idea.gitRepoIconProvider")

    internal fun getIcon(project: Project, repositoryId: RepositoryId) =
      EP_NAME.computeSafeIfAny { it.getIcon(project, repositoryId) } ?: PlatformIcons.FOLDER_ICON
  }
}