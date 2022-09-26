// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.actions.ShowAnnotateOperationsPopup
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.annotate.GitFileAnnotation
import git4idea.remote.hosting.findKnownRepositories
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager


class GHOpenInBrowserFromAnnotationActionGroup(val annotation: FileAnnotation)
  : GHOpenInBrowserActionGroup() {
  override fun getData(dataContext: DataContext): List<Data>? {
    val myLineNumber = ShowAnnotateOperationsPopup.getAnnotationLineNumber(dataContext)
    if (myLineNumber < 0) return null

    if (annotation !is GitFileAnnotation) return null
    val project = annotation.project
    val virtualFile = annotation.file

    val filePath = VcsUtil.getFilePath(virtualFile)
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath) ?: return null

    val accessibleRepositories = project.service<GHHostedRepositoriesManager>().findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    val revisionHash = annotation.getLineRevisionNumber(myLineNumber)?.asString()
    if (revisionHash == null) return null

    return accessibleRepositories.map { Data.Revision(project, it.repository, revisionHash) }
  }
}
