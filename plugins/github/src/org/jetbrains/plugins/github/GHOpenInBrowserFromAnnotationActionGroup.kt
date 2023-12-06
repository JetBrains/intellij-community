// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.actions.ShowAnnotateOperationsPopup
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.util.asSafely
import git4idea.GitRevisionNumber
import git4idea.annotate.GitFileAnnotation
import git4idea.remote.hosting.action.HostedGitRepositoryReference
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceActionGroup
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

internal class GHOpenInBrowserFromAnnotationActionGroup(val annotation: FileAnnotation)
  : HostedGitRepositoryReferenceActionGroup(GithubBundle.messagePointer("open.on.github.action"),
                                            GithubBundle.messagePointer("open.on.github.action.description"),
                                            { AllIcons.Vcs.Vendors.Github }) {
  override fun findReferences(dataContext: DataContext): List<HostedGitRepositoryReference> {
    if (annotation !is GitFileAnnotation) return emptyList()
    val project = annotation.project
    val virtualFile = annotation.file

    val revision = ShowAnnotateOperationsPopup.getAnnotationLineNumber(dataContext).takeIf { it >= 0 }?.let {
      annotation.getLineRevisionNumber(it)
    }?.asSafely<GitRevisionNumber>() ?: return emptyList()

    return HostedGitRepositoryReferenceUtil
      .findReferences(project, project.service<GHHostedRepositoriesManager>(), virtualFile, revision, GHPathUtil::getWebURI)
  }

  override fun handleReference(reference: HostedGitRepositoryReference) {
    val uri = reference.buildWebURI() ?: return
    BrowserUtil.browse(uri)
  }
}
