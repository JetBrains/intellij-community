// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gitlab.ui.action

import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.action.GlobalHostedGitRepositoryReferenceActionGroup
import git4idea.remote.hosting.action.HostedGitRepositoryReference
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import java.awt.datatransfer.StringSelection
import java.net.URI

class GitLabCopyLinkActionGroup : GlobalHostedGitRepositoryReferenceActionGroup() {
  override fun repositoriesManager(project: Project): HostedGitRepositoriesManager<*> =
    project.service<GitLabProjectsManager>()

  override fun getUri(project: Project, repository: URI, revisionHash: String): URI =
    GitLabURIUtil.getWebURI(project, repository, revisionHash)

  override fun getUri(repository: URI, revisionHash: String): URI =
    GitLabURIUtil.getWebURI(null, repository, revisionHash)

  override fun getUri(project: Project, repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?): URI =
    GitLabURIUtil.getWebURI(project, repository, revisionHash, relativePath, lineRange)

  override fun getUri(repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?): URI =
    GitLabURIUtil.getWebURI(null, repository, revisionHash, relativePath, lineRange)

  override fun handleReference(reference: HostedGitRepositoryReference) {
    val uri = reference.buildWebURI()?.toString() ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(uri))
  }
}
