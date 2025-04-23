// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.action

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.actions.ShowAnnotateOperationsPopup
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.util.asSafely
import com.intellij.vcs.gitlab.icons.GitlabIcons
import git4idea.GitRevisionNumber
import git4idea.annotate.GitFileAnnotation
import git4idea.remote.hosting.action.HostedGitRepositoryReference
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceActionGroup
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceUtil
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabOpenInBrowserFromAnnotationActionGroup(val annotation: FileAnnotation)
  : HostedGitRepositoryReferenceActionGroup(GitLabBundle.messagePointer("group.GitLab.Open.In.Browser.text"),
                                            GitLabBundle.messagePointer("group.GitLab.Open.In.Browser.description"),
                                            { GitlabIcons.GitLabLogo }) {

  override fun findReferences(dataContext: DataContext): List<HostedGitRepositoryReference> {
    if (annotation !is GitFileAnnotation) return emptyList()
    val project = annotation.project
    val virtualFile = annotation.file

    val revision = ShowAnnotateOperationsPopup.getAnnotationLineNumber(dataContext).takeIf { it >= 0 }?.let {
      annotation.getLineRevisionNumber(it)
    }?.asSafely<GitRevisionNumber>() ?: return emptyList()

    return HostedGitRepositoryReferenceUtil
      .findReferences(project, project.service<GitLabProjectsManager>(), virtualFile, revision) { repository, revisionHash ->
        GitLabURIUtil.getWebURI(project, repository, revisionHash)
      }
  }

  override fun handleReference(reference: HostedGitRepositoryReference) {
    val uri = reference.buildWebURI() ?: return
    BrowserUtil.browse(uri)
  }
}

class GitLabAnnotationGutterActionProvider : AnnotationGutterActionProvider {
  override fun createAction(annotation: FileAnnotation): AnAction = GitLabOpenInBrowserFromAnnotationActionGroup(annotation)
}


