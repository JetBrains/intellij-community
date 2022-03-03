// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitFileRevision
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import org.apache.commons.httpclient.util.URIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUtil

open class GHOpenInBrowserActionGroup
  : ActionGroup(GithubBundle.messagePointer("open.on.github.action"),
                GithubBundle.messagePointer("open.on.github.action.description"),
                AllIcons.Vcs.Vendors.Github), DumbAware {

  override fun update(e: AnActionEvent) {
    val data = getData(e.dataContext)
    e.presentation.isEnabledAndVisible = data != null && data.isNotEmpty()
    e.presentation.isPerformGroup = data?.size == 1
    e.presentation.isPopupGroup = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    e ?: return emptyArray()
    val data = getData(e.dataContext) ?: return emptyArray()
    if (data.size <= 1) return emptyArray()

    return data.map { GithubOpenInBrowserAction(it) }.toTypedArray()
  }

  override fun actionPerformed(e: AnActionEvent) {
    getData(e.dataContext)?.let { GithubOpenInBrowserAction(it.first()) }?.actionPerformed(e)
  }

  override fun disableIfNoVisibleChildren(): Boolean = false

  protected open fun getData(dataContext: DataContext): List<Data>? {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return null

    return getDataFromPullRequest(project, dataContext)
           ?: getDataFromHistory(project, dataContext)
           ?: getDataFromLog(project, dataContext)
           ?: getDataFromVirtualFile(project, dataContext)
  }

  private fun getDataFromPullRequest(project: Project, dataContext: DataContext): List<Data>? {
    val pullRequest = dataContext.getData(GHPRActionKeys.SELECTED_PULL_REQUEST)
                      ?: dataContext.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)?.detailsData?.loadedDetails
                      ?: return null
    return listOf(Data.URL(project, pullRequest.url))
  }

  private fun getDataFromHistory(project: Project, dataContext: DataContext): List<Data>? {
    val fileRevision = dataContext.getData(VcsDataKeys.VCS_FILE_REVISION) ?: return null
    if (fileRevision !is GitFileRevision) return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(fileRevision.path)
    if (repository == null) return null

    val accessibleRepositories = project.service<GHProjectRepositoriesManager>().findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    return accessibleRepositories.map { Data.Revision(project, it.ghRepositoryCoordinates, fileRevision.revisionNumber.asString()) }
  }

  private fun getDataFromLog(project: Project, dataContext: DataContext): List<Data>? {
    val log = dataContext.getData(VcsLogDataKeys.VCS_LOG) ?: return null

    val selectedCommits = log.selectedCommits
    if (selectedCommits.size != 1) return null

    val commit = ContainerUtil.getFirstItem(selectedCommits) ?: return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(commit.root)
    if (repository == null) return null


    val accessibleRepositories = project.service<GHProjectRepositoriesManager>().findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    return accessibleRepositories.map { Data.Revision(project, it.ghRepositoryCoordinates, commit.hash.asString()) }
  }

  private fun getDataFromVirtualFile(project: Project, dataContext: DataContext): List<Data>? {
    val virtualFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(virtualFile)
    if (repository == null) return null


    val accessibleRepositories = project.service<GHProjectRepositoriesManager>().findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    val changeListManager = ChangeListManager.getInstance(project)
    if (changeListManager.isUnversioned(virtualFile)) return null

    val change = changeListManager.getChange(virtualFile)
    return if (change != null && change.type == Change.Type.NEW) null
    else accessibleRepositories.map { Data.File(project, it.ghRepositoryCoordinates, repository.root, virtualFile) }
  }

  protected sealed class Data(val project: Project) {

    @Nls
    abstract fun getName(): String

    class File(project: Project,
               val repository: GHRepositoryCoordinates,
               val gitRepoRoot: VirtualFile,
               val virtualFile: VirtualFile) : Data(project) {
      override fun getName(): String {
        @NlsSafe
        val formatted = repository.toString().replace('_', ' ')
        return formatted
      }
    }

    class Revision(project: Project, val repository: GHRepositoryCoordinates, val revisionHash: String) : Data(project) {
      override fun getName(): String {
        @NlsSafe
        val formatted = repository.toString().replace('_', ' ')
        return formatted
      }
    }

    class URL(project: Project, @NlsSafe val htmlUrl: String) : Data(project) {
      override fun getName() = htmlUrl
    }
  }

  private companion object {
    class GithubOpenInBrowserAction(val data: Data)
      : DumbAwareAction({ data.getName() }) {

      override fun actionPerformed(e: AnActionEvent) {
        when (data) {
          is Data.Revision -> openCommitInBrowser(data.repository, data.revisionHash)
          is Data.File -> openFileInBrowser(data.project, data.gitRepoRoot, data.repository, data.virtualFile,
                                            e.getData(CommonDataKeys.EDITOR))
          is Data.URL -> BrowserUtil.browse(data.htmlUrl)
        }
      }

      private fun openCommitInBrowser(path: GHRepositoryCoordinates, revisionHash: String) {
        BrowserUtil.browse("${path.toUrl()}/commit/$revisionHash")
      }

      private fun openFileInBrowser(project: Project,
                                    repositoryRoot: VirtualFile,
                                    path: GHRepositoryCoordinates,
                                    virtualFile: VirtualFile,
                                    editor: Editor?) {
        val relativePath = VfsUtilCore.getRelativePath(virtualFile, repositoryRoot)
        if (relativePath == null) {
          GithubNotifications.showError(project, GithubNotificationIdsHolder.OPEN_IN_BROWSER_FILE_IS_NOT_UNDER_REPO,
                                        GithubBundle.message("cannot.open.in.browser"),
                                        GithubBundle.message("open.on.github.file.is.not.under.repository"),
                                        "Root: " + repositoryRoot.presentableUrl + ", file: " + virtualFile.presentableUrl)
          return
        }

        val hash = getCurrentFileRevisionHash(project, virtualFile)
        if (hash == null) {
          GithubNotifications.showError(project,
                                        GithubNotificationIdsHolder.OPEN_IN_BROWSER_CANNOT_GET_LAST_REVISION,
                                        GithubBundle.message("cannot.open.in.browser"),
                                        GithubBundle.message("cannot.get.last.revision"))
          return
        }

        val githubUrl = GHPathUtil.makeUrlToOpen(editor, relativePath, hash, path)
        if (githubUrl != null) BrowserUtil.browse(githubUrl)
      }

      private fun getCurrentFileRevisionHash(project: Project, file: VirtualFile): String? {
        val ref = Ref<GitRevisionNumber>()
        object : Task.Modal(project, GithubBundle.message("open.on.github.getting.last.revision"), true) {
          override fun run(indicator: ProgressIndicator) {
            ref.set(GitHistoryUtils.getCurrentRevision(project, VcsUtil.getFilePath(file), "HEAD") as GitRevisionNumber?)
          }

          override fun onThrowable(error: Throwable) {
            GithubUtil.LOG.warn(error)
          }
        }.queue()
        return if (ref.isNull) null else ref.get().rev
      }
    }
  }
}

object GHPathUtil {
  fun getFileURL(repository: GitRepository,
                 path: GHRepositoryCoordinates,
                 virtualFile: VirtualFile,
                 editor: Editor?): String? {
    val relativePath = VfsUtilCore.getRelativePath(virtualFile, repository.root)
    if (relativePath == null) {
      return null
    }

    val hash = repository.currentRevision
    if (hash == null) {
      return null
    }

    return makeUrlToOpen(editor, relativePath, hash, path)
  }

  fun makeUrlToOpen(editor: Editor?, relativePath: String, branch: String, path: GHRepositoryCoordinates): String? {
    val builder = StringBuilder()

    if (StringUtil.isEmptyOrSpaces(relativePath)) {
      builder.append(path.toUrl()).append("/tree/").append(branch)
    }
    else {
      builder.append(path.toUrl()).append("/blob/").append(branch).append('/').append(URIUtil.encodePath(relativePath))
    }

    if (editor != null && editor.document.lineCount >= 1) {
      // lines are counted internally from 0, but from 1 on github
      val selectionModel = editor.selectionModel
      val begin = editor.document.getLineNumber(selectionModel.selectionStart) + 1
      val selectionEnd = selectionModel.selectionEnd
      var end = editor.document.getLineNumber(selectionEnd) + 1
      if (editor.document.getLineStartOffset(end - 1) == selectionEnd) {
        end -= 1
      }
      builder.append("#L").append(begin)
      if (begin != end) {
        builder.append("-L").append(end)
      }
    }
    return builder.toString()
  }
}