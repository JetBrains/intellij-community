// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.github.git.share

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.workspace.SubprojectInfoProvider
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.containers.mapSmartSet
import git4idea.DialogManager
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.GitShareProjectService
import git4idea.remote.hosting.knownRepositories
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.GHShareProjectCompatibilityExtension
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.data.request.Type
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.GithubShareDialog
import org.jetbrains.plugins.github.util.GHCompatibilityUtil
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubSettings
import java.awt.Component
import java.util.*

private class GHShareProjectUtilCompatExtension : GHShareProjectCompatibilityExtension {
  override fun shareProjectOnGithub(project: Project, file: VirtualFile?) {
    GHShareProjectUtil.shareProjectOnGithub(project, file)
  }
}

object GHShareProjectUtil {
  @JvmStatic
  fun shareProjectOnGithub(project: Project, file: VirtualFile?) {
    val gitRepository = GithubGitHelper.findGitRepository(project, file)
    val root = gitRepository?.root ?: project.baseDir
    val projectName = file?.let { SubprojectInfoProvider.getSubprojectName(project, file) } ?: project.name
    shareProjectOnGithub(project, gitRepository, root, projectName)
  }

  fun shareProjectOnGithub(
    project: Project,
    gitRepository: GitRepository?,
    root: VirtualFile,
    projectName: @NlsSafe String,
  ) {
    FileDocumentManager.getInstance().saveAllDocuments()

    val githubServiceName = GithubBundle.message("settings.configurable.display.name")

    val gitSpService = project.service<GitShareProjectService>()
    val projectManager = project.service<GHHostedRepositoriesManager>()
    if (!gitSpService.showExistingRemotesDialog(githubServiceName, gitRepository, projectManager.knownRepositories))
      return

    val shareDialogResult = showShareProjectDialog(project, gitRepository, projectName) ?: return

    val account: GithubAccount = shareDialogResult.account!!

    val token = GHCompatibilityUtil.getOrRequestToken(account, project, GHLoginSource.SHARE) ?: return
    val requestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

    gitSpService.performShareProject(
      githubServiceName, gitRepository, root, shareDialogResult.repositoryName, shareDialogResult.remoteName,
      createRepo = { createRepo(requestExecutor, shareDialogResult) },
      extractRepoWebUrl = { it.htmlUrl },
      extractRepoRemoteUrl = {
        val sshUrl = it.sshUrl
        if (GithubSettings.getInstance().isCloneGitUsingSsh && sshUrl != null) sshUrl else it.cloneUrl
      }
    )
  }

  private suspend fun createRepo(
    requestExecutor: GithubApiRequestExecutor,
    shareDialogResult: GithubShareDialog.Result,
  ): GithubRepo {
    val name: String = shareDialogResult.repositoryName
    val description: String = shareDialogResult.description
    val isPrivate: Boolean = shareDialogResult.isPrivate
    val account: GithubAccount = shareDialogResult.account!!

    return requestExecutor.executeSuspend(GithubApiRequests.CurrentUser.Repos.create(account.server, name, description, isPrivate))
  }

  private fun showShareProjectDialog(
    project: Project,
    gitRepository: GitRepository?,
    projectName: String,
  ): GithubShareDialog.Result? {
    val loadedInfo = Collections.synchronizedMap(mutableMapOf<GithubAccount, Pair<Boolean, Set<String>>>())

    val shareDialog = GithubShareDialog(
      project,
      gitRepository?.remotes?.map { it.name }?.toSet() ?: emptySet(),
      { account, comp -> loadedInfo.getOrPut(account) { loadEnsuringTokenExistsToken(project, account, comp) } },
      projectName
    )

    DialogManager.show(shareDialog)

    return if (shareDialog.isOK) shareDialog.getResult() else null
  }

  private suspend fun loadEnsuringTokenExistsToken(project: Project, account: GithubAccount, comp: Component): Pair<Boolean, Set<String>> {
    while (true) {
      try {
        return withModalProgress(ModalTaskOwner.project(project), GitBundle.message("share.process.loading.account.info", account), TaskCancellation.cancellable()) {
          val token = serviceAsync<GHAccountManager>().findCredentials(account) ?: throw GithubMissingTokenException(account)
          val requestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

          val user = requestExecutor.executeSuspend(GithubApiRequests.CurrentUser.get(account.server))
          val names = GithubApiPagesLoader.loadAll(requestExecutor, GithubApiRequests.CurrentUser.Repos.pages(account.server, type = Type.OWNER))
            .mapSmartSet { it.name }

          user.canCreatePrivateRepo() to names
        }
      }
      catch (mte: GithubMissingTokenException) {
        withContext(Dispatchers.EDT) {
          GHAccountsUtil.requestNewToken(account, project, comp, GHLoginSource.SHARE) ?: throw mte
        }
      }
    }
  }
}
