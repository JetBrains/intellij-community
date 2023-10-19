// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.action

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.vcs.log.VcsLogDataKeys
import git4idea.GitFileRevision
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceUtil.findReferences
import java.net.URI
import java.util.function.Supplier
import javax.swing.Icon

abstract class GlobalHostedGitRepositoryReferenceActionGroup : HostedGitRepositoryReferenceActionGroup {
  constructor() : super()

  constructor(
    dynamicText: Supplier<@NlsActions.ActionText String>,
    dynamicDescription: Supplier<@NlsActions.ActionDescription String>,
    icon: Supplier<Icon?>?
  ) : super(dynamicText, dynamicDescription, icon)

  protected abstract fun repositoriesManager(project: Project): HostedGitRepositoriesManager<*>

  override fun findReferences(dataContext: DataContext): List<HostedGitRepositoryReference> {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return emptyList()

    return findReferencesInHistory(project, dataContext).takeIf { it.isNotEmpty() }
           ?: findReferencesInLog(project, dataContext).takeIf { it.isNotEmpty() }
           ?: findReferencesInFile(project, dataContext)
  }

  private fun findReferencesInHistory(project: Project, dataContext: DataContext): List<HostedGitRepositoryReference> {
    val fileRevision = dataContext.getData(VcsDataKeys.VCS_FILE_REVISION) ?: return emptyList()
    if (fileRevision !is GitFileRevision) return emptyList()
    return findReferences(project, repositoriesManager(project), fileRevision, ::getUri)
  }

  private fun findReferencesInLog(project: Project, dataContext: DataContext): List<HostedGitRepositoryReference> {
    val commit = dataContext.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.firstOrNull() ?: return emptyList()
    return findReferences(project, repositoriesManager(project), commit, ::getUri)
  }

  private fun findReferencesInFile(project: Project, dataContext: DataContext): List<HostedGitRepositoryReference> {
    val virtualFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyList()
    val editor = dataContext.getData(CommonDataKeys.EDITOR)

    return findReferences(project, repositoriesManager(project), virtualFile, editor, ::getUri)
  }

  protected abstract fun getUri(repository: URI, revisionHash: String): URI

  protected abstract fun getUri(repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?): URI
}