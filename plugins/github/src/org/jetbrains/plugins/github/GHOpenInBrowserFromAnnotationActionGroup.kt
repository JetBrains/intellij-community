// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.annotate.GitFileAnnotation
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.util.GithubGitHelper


class GHOpenInBrowserFromAnnotationActionGroup(val annotation: FileAnnotation)
  : GHOpenInBrowserActionGroup(), UpToDateLineNumberListener {
  private var myLineNumber = -1

  override fun getData(dataContext: DataContext): Pair<Set<GHRepositoryCoordinates>, Data>? {
    if (myLineNumber < 0) return null

    if (annotation !is GitFileAnnotation) return null
    val project = annotation.project
    val virtualFile = annotation.file

    val filePath = VcsUtil.getFilePath(virtualFile)
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath) ?: return null

    val accessibleRepositories = service<GithubGitHelper>().getPossibleRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    val revisionHash = annotation.getLineRevisionNumber(myLineNumber)?.asString()
    if (revisionHash == null) return null

    return accessibleRepositories to Data.Revision(project, revisionHash)
  }

  override fun consume(integer: Int) {
    myLineNumber = integer
  }
}
