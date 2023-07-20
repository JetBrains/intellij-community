// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.actions.ShowAnnotateOperationsPopup
import com.intellij.openapi.vcs.annotate.FileAnnotation
import git4idea.annotate.GitFileAnnotation
import git4idea.remote.hosting.action.HostedGitRepositoryReference
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceUtil
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceActionGroup
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager


class GHOpenInBrowserFromAnnotationActionGroup(val annotation: FileAnnotation)
  : HostedGitRepositoryReferenceActionGroup(GithubBundle.messagePointer("open.on.github.action"),
                                            GithubBundle.messagePointer("open.on.github.action.description"),
                                            AllIcons.Vcs.Vendors.Github) {
  override fun findReferences(dataContext: DataContext): List<HostedGitRepositoryReference> {
    if (annotation !is GitFileAnnotation) return emptyList()

    val lineNumber = ShowAnnotateOperationsPopup.getAnnotationLineNumber(dataContext)
    if (lineNumber < 0) return emptyList()

    val project = annotation.project
    val virtualFile = annotation.file

    return HostedGitRepositoryReferenceUtil.findReferences(project, project.service<GHHostedRepositoriesManager>(), virtualFile,
                                                           lineNumber..lineNumber, GHPathUtil::getWebURI)
  }

  override fun handleReference(reference: HostedGitRepositoryReference) {
    val uri = reference.buildWebURI() ?: return
    BrowserUtil.browse(uri)
  }
}
