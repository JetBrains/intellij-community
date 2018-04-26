// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.UpToDateLineNumberListener
import git4idea.GitUtil
import org.jetbrains.plugins.github.api.GithubRepositoryPath
import org.jetbrains.plugins.github.util.GithubGitHelper


class GithubOpenInBrowserFromAnnotationActionGroup(val annotation: FileAnnotation)
  : GithubOpenInBrowserActionGroup(), UpToDateLineNumberListener {
  private var myLineNumber = -1

  override fun getData(dataContext: DataContext): Pair<Set<GithubRepositoryPath>, Data>? {
    if (myLineNumber < 0) return null

    val project = dataContext.getData(CommonDataKeys.PROJECT)
    val virtualFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || virtualFile == null) return null

    FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(virtualFile)
    if (repository == null) return null

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
