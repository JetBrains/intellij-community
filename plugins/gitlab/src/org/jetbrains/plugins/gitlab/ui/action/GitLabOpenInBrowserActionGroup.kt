// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.action

import com.intellij.collaboration.util.resolveRelative
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.io.URLUtil
import com.intellij.util.withFragment
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.action.GlobalHostedGitRepositoryReferenceActionGroup
import git4idea.remote.hosting.action.HostedGitRepositoryReference
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import java.net.URI

class GitLabOpenInBrowserActionGroup
  : GlobalHostedGitRepositoryReferenceActionGroup() {
  override fun repositoriesManager(project: Project): HostedGitRepositoriesManager<*> =
    project.service<GitLabProjectsManager>()

  override fun getUri(repository: URI, revisionHash: String): URI =
    GitLabURIUtil.getWebURI(repository, revisionHash)

  override fun getUri(repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?): URI =
    GitLabURIUtil.getWebURI(repository, revisionHash, relativePath, lineRange)

  override fun handleReference(reference: HostedGitRepositoryReference) {
    val uri = reference.buildWebURI() ?: return
    BrowserUtil.browse(uri)
  }
}

object GitLabURIUtil {
  fun getWebURI(repository: URI, revisionOrBranch: String): URI =
    repository.resolveRelative("commit").resolveRelative(revisionOrBranch)

  fun getWebURI(repository: URI, revisionOrBranch: String, relativePath: String, lineRange: IntRange?): URI {
    val fileUri = repository.resolveRelative("blob").resolveRelative(revisionOrBranch).resolveRelative(URLUtil.encodePath(relativePath))

    return if (lineRange != null) {
      val fragmentBuilder = StringBuilder()
      fragmentBuilder.append("L").append(lineRange.first + 1)
      if (lineRange.last != lineRange.first) {
        fragmentBuilder.append("-L").append(lineRange.last + 1)
      }
      fileUri.withFragment(fragmentBuilder.toString())
    }
    else {
      fileUri
    }
  }
}