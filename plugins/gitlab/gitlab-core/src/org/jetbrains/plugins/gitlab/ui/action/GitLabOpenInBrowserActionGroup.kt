// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.action

import com.intellij.collaboration.util.URIUtil.encodePath
import com.intellij.collaboration.util.resolveRelative
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.action.GlobalHostedGitRepositoryReferenceActionGroup
import git4idea.remote.hosting.action.HostedGitRepositoryReference
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import java.net.URI

class GitLabOpenInBrowserActionGroup
  : GlobalHostedGitRepositoryReferenceActionGroup() {
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
    val uri = reference.buildWebURI() ?: return
    BrowserUtil.browse(uri)
  }
}

//https://gitlab.com/gitlab-org/gitlab/-/issues/28848#release-notes
object GitLabURIUtil {
  private fun getServerVersion(project: Project): GitLabVersion? =
    project.service<GitLabProjectConnectionManager>().connectionState.value?.serverVersion

  fun getWebURI(project: Project?, repository: URI, revisionOrBranch: String): URI {
    val version = project?.let { getServerVersion(it) }

    // if version == null, it must be gitlab.com, otherwise a weburl would not be shown
    return if (version != null && version < GitLabVersion(16, 0)) repository.resolveRelative("commit").resolveRelative(revisionOrBranch)
    else repository.resolveRelative("-").resolveRelative("commit").resolveRelative(revisionOrBranch)
  }

  fun getWebURI(project: Project?, repository: URI, revisionOrBranch: String, relativePath: String, lineRange: IntRange?): URI {
    val version = project?.let { getServerVersion(it) }

    // if version == null, it must be gitlab.com, otherwise a weburl would not be shown
    val fileUri =
      if (version != null && version < GitLabVersion(16, 0)) repository.resolveRelative("blob").resolveRelative(revisionOrBranch)
      else repository.resolveRelative("-").resolveRelative("blob").resolveRelative(revisionOrBranch)
    val encodedFileUri = fileUri.resolveRelative(encodePath(relativePath))
    if (lineRange == null) return encodedFileUri

    val fragment = buildString {
      append("L").append(lineRange.first + 1)
      if (lineRange.last != lineRange.first) {
        append("-L").append(lineRange.last + 1)
      }
    }
    return URI("$encodedFileUri#$fragment")
  }
}