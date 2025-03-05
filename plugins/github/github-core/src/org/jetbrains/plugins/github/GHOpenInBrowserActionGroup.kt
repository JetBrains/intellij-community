// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.collaboration.util.resolveRelative
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.io.URLUtil
import com.intellij.util.withFragment
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.action.GlobalHostedGitRepositoryReferenceActionGroup
import git4idea.remote.hosting.action.HostedGitRepositoryReference
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.net.URI

class GHOpenInBrowserActionGroup
  : GlobalHostedGitRepositoryReferenceActionGroup(GithubBundle.messagePointer("open.on.github.action"),
                                                  GithubBundle.messagePointer("open.on.github.action.description"),
                                                  { AllIcons.Vcs.Vendors.Github }) {
  override fun repositoriesManager(project: Project): HostedGitRepositoriesManager<*> {
    return project.service<GHHostedRepositoriesManager>()
  }

  override fun getUri(repository: URI, revisionHash: String): URI =
    GHPathUtil.getWebURI(repository, revisionHash)

  override fun getUri(repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?): URI =
    GHPathUtil.getWebURI(repository, revisionHash, relativePath, lineRange)

  override fun handleReference(reference: HostedGitRepositoryReference) {
    val uri = reference.buildWebURI() ?: return
    BrowserUtil.browse(uri)
  }
}

object GHPathUtil {
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