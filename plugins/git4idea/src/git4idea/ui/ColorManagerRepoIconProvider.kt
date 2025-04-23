// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getRepositoryIcon
import com.intellij.icons.AllIcons
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.ui.GitRepoIconProvider
import javax.swing.Icon

private val LOG = Logger.getInstance(ColorManagerRepoIconProvider::class.java)

class ColorManagerRepoIconProvider : GitRepoIconProvider {
  override fun getIcon(project: Project, repositoryId: RepositoryId): Icon {
    val colorManager = RepositoryChangesBrowserNode.getColorManager(project)
    val virtualFile = repositoryId.rootPath.virtualFile()
    if (virtualFile == null) {
      LOG.error("Failed to deserialize virtual file id for repository root: ${repositoryId.rootPath}")
      return AllIcons.Empty
    }

    return getRepositoryIcon(colorManager, virtualFile)
  }
}