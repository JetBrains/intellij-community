// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getRepositoryIcon
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.ui.GitRepoIconProvider
import git4idea.repo.GitRepositoryIdCache
import javax.swing.Icon

class ColorManagerRepoIconProvider : GitRepoIconProvider {
  override fun getIcon(project: Project, repositoryId: RepositoryId): Icon {
    val colorManager = RepositoryChangesBrowserNode.getColorManager(project)
    val root = GitRepositoryIdCache.getInstance(project).get(repositoryId)?.root
    return if (root == null) AllIcons.Empty else getRepositoryIcon(colorManager, root)
  }
}