// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUtil

open class GithubOpenInBrowserActionGroup
  : ActionGroup("Open on GitHub", "Open corresponding link in browser", AllIcons.Vcs.Vendors.Github), DumbAware {

  override fun update(e: AnActionEvent) {
    val repositories = getData(e.dataContext)?.first
    e.presentation.isEnabledAndVisible = repositories != null && repositories.isNotEmpty()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    e ?: return emptyArray()
    val data = getData(e.dataContext) ?: return emptyArray()
    if (data.first.size <= 1) return emptyArray()

    return data.first.map { GithubOpenInBrowserAction(it, data.second) }.toTypedArray()
  }

  override fun isPopup(): Boolean = true

  override fun actionPerformed(e: AnActionEvent) {
    getData(e.dataContext)?.let { GithubOpenInBrowserAction(it.first.first(), it.second) }?.actionPerformed(e)
  }

  override fun canBePerformed(context: DataContext): Boolean {
    return getData(context)?.first?.size == 1
  }

  override fun disableIfNoVisibleChildren(): Boolean = false

  protected open fun getData(dataContext: DataContext): Pair<Set<GHRepositoryCoordinates>, Data>? {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return null

    return getDataFromPullRequest(project, dataContext)
           ?: getDataFromHistory(project, dataContext)
           ?: getDataFromLog(project, dataContext)
           ?: getDataFromVirtualFile(project, dataContext)
  }

  private fun getDataFromPullRequest(project: Project, dataContext: DataContext): Pair<Set<GHRepositoryCoordinates>, Data>? {
    val pullRequest = dataContext.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST) ?: return null
    val context = dataContext.getData(GithubPullRequestKeys.ACTION_DATA_CONTEXT) ?: return null

    return setOf(context.repositoryCoordinates) to Data.URL(project, pullRequest.url)
  }

  private fun getDataFromHistory(project: Project, dataContext: DataContext): Pair<Set<GHRepositoryCoordinates>, Data>? {
    val filePath = dataContext.getData(VcsDataKeys.FILE_PATH) ?: return null
    val fileRevision = dataContext.getData(VcsDataKeys.VCS_FILE_REVISION) ?: return null
    if (fileRevision !is GitFileRevision) return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(filePath)
    if (repository == null) return null

    val accessibleRepositories = service<GithubGitHelper>().getPossibleRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    return accessibleRepositories to Data.Revision(project, fileRevision.revisionNumber.asString())
  }

  private fun getDataFromLog(project: Project, dataContext: DataContext): Pair<Set<GHRepositoryCoordinates>, Data>? {
    val log = dataContext.getData(VcsLogDataKeys.VCS_LOG) ?: return null

    val selectedCommits = log.selectedCommits
    if (selectedCommits.size != 1) return null

    val commit = ContainerUtil.getFirstItem(selectedCommits) ?: return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(commit.root)
    if (repository == null) return null

    val accessibleRepositories = service<GithubGitHelper>().getPossibleRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    return accessibleRepositories to Data.Revision(project, commit.hash.asString())
  }

  private fun getDataFromVirtualFile(project: Project, dataContext: DataContext): Pair<Set<GHRepositoryCoordinates>, Data>? {
    val virtualFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(virtualFile)
    if (repository == null) return null

    val accessibleRepositories = service<GithubGitHelper>().getPossibleRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    val changeListManager = ChangeListManager.getInstance(project)
    if (changeListManager.isUnversioned(virtualFile)) return null

    val change = changeListManager.getChange(virtualFile)
    return if (change != null && change.type == Change.Type.NEW) null
    else accessibleRepositories to Data.File(project, repository.root, virtualFile)
  }

  protected sealed class Data(val project: Project) {
    class File(project: Project, val gitRepoRoot: VirtualFile, val virtualFile: VirtualFile) : Data(project)

    class Revision(project: Project, val revisionHash: String) : Data(project)

    class URL(project: Project, val htmlUrl: String) : Data(project)
  }

  private companion object {
    private const val CANNOT_OPEN_IN_BROWSER = "Can't open in browser"

    class GithubOpenInBrowserAction(private val repoPath: GHRepositoryCoordinates, val data: Data)
      : DumbAwareAction(repoPath.toString().replace('_', ' ')) {

      override fun actionPerformed(e: AnActionEvent) {
        when (data) {
          is Data.Revision -> openCommitInBrowser(repoPath, data.revisionHash)
          is Data.File -> openFileInBrowser(data.project, data.gitRepoRoot, repoPath, data.virtualFile, e.getData(CommonDataKeys.EDITOR))
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
          GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, "File is not under repository root",
                                        "Root: " + repositoryRoot.presentableUrl + ", file: " + virtualFile.presentableUrl)
          return
        }

        val hash = getCurrentFileRevisionHash(project, virtualFile)
        if (hash == null) {
          GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, "Can't get last revision.")
          return
        }

        val githubUrl = makeUrlToOpen(editor, relativePath, hash, path)
        if (githubUrl != null) BrowserUtil.browse(githubUrl)
      }

      private fun getCurrentFileRevisionHash(project: Project, file: VirtualFile): String? {
        val ref = Ref<GitRevisionNumber>()
        object : Task.Modal(project, "Getting Last Revision", true) {
          override fun run(indicator: ProgressIndicator) {
            ref.set(GitHistoryUtils.getCurrentRevision(project, VcsUtil.getFilePath(file), "HEAD") as GitRevisionNumber?)
          }

          override fun onThrowable(error: Throwable) {
            GithubUtil.LOG.warn(error)
          }
        }.queue()
        return if (ref.isNull) null else ref.get().rev
      }

      private fun makeUrlToOpen(editor: Editor?,
                                relativePath: String,
                                branch: String,
                                path: GHRepositoryCoordinates): String? {
        val builder = StringBuilder()

        if (StringUtil.isEmptyOrSpaces(relativePath)) {
          builder.append(path.toUrl()).append("/tree/").append(branch)
        }
        else {
          builder.append(path.toUrl()).append("/blob/").append(branch).append('/').append(relativePath)
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
  }
}