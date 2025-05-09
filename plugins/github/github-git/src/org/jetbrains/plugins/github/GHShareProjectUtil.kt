// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.workspace.SubprojectInfoProvider
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.mapSmartSet
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.actions.GitInit
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.findKnownRepositories
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.request.Type
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GHCachingAccountInformationProvider
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.git.share.dialog.GithubExistingRemotesDialog
import org.jetbrains.plugins.github.git.share.dialog.GithubUntrackedFilesDialog
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.GithubShareDialog
import org.jetbrains.plugins.github.util.*
import java.awt.Component
import java.util.*

object GHShareProjectUtil {
  @JvmStatic
  fun shareProjectOnGithub(project: Project, file: VirtualFile?) {
    val gitRepository = GithubGitHelper.findGitRepository(project, file)
    val root = gitRepository?.root ?: project.baseDir
    val projectName = file?.let { SubprojectInfoProvider.getSubprojectName(project, file) } ?: project.name
    shareProjectOnGithub(project, gitRepository, root, projectName)
  }

  // get gitRepository
  // check for existing git repo
  // check available repos and privateRepo access (net)
  // Show dialog (window)
  // create GitHub repo (net)
  // create local git repo (if not exist)
  // add GitHub as a remote host
  // make first commit
  // push everything (net)
  fun shareProjectOnGithub(
    project: Project,
    gitRepository: GitRepository?,
    root: VirtualFile,
    projectName: @NlsSafe String,
  ) {
    FileDocumentManager.getInstance().saveAllDocuments()

    val possibleRemotes = gitRepository
      ?.let(project.service<GHHostedRepositoriesManager>()::findKnownRepositories)
      ?.map { it.remote.url }.orEmpty()
    if (possibleRemotes.isNotEmpty()) {
      val existingRemotesDialog = GithubExistingRemotesDialog(project, possibleRemotes)
      DialogManager.show(existingRemotesDialog)
      if (!existingRemotesDialog.isOK) {
        return
      }
    }

    val spService = project.service<GHShareProjectService>()

    val shareDialogResult = spService.showShareProjectDialog(gitRepository, projectName) ?: return
    spService.performShareProjectOnGithub(gitRepository, root, shareDialogResult)
  }
}

@Service(Service.Level.PROJECT)
private class GHShareProjectService(private val project: Project, private val cs: CoroutineScope) {
  companion object {
    private val LOG = logger<GHShareProjectService>()
  }

  fun showShareProjectDialog(
    gitRepository: GitRepository?,
    projectName: String,
  ): GithubShareDialog.Result? {
    val cs = cs.childScope("showShareProjectDialog")

    val loadedInfo = Collections.synchronizedMap(mutableMapOf<GithubAccount, Pair<Boolean, Set<String>>>())

    val shareDialog = GithubShareDialog(
      project,
      cs,
      gitRepository?.remotes?.map { it.name }?.toSet() ?: emptySet(),
      { account, comp -> loadedInfo.getOrPut(account) { loadEnsuringTokenExistsToken(account, comp) } },
      projectName
    )

    DialogManager.show(shareDialog)

    return if (shareDialog.isOK) shareDialog.getResult() else null
  }

  // Service
  private suspend fun loadEnsuringTokenExistsToken(account: GithubAccount, comp: Component): Pair<Boolean, Set<String>> {
    while (true) {
      try {
        return withModalProgress(ModalTaskOwner.project(project), GithubBundle.message("share.process.loading.account.info", account), TaskCancellation.cancellable()) {
          val token = serviceAsync<GHAccountManager>().findCredentials(account) ?: throw GithubMissingTokenException(account)
          val requestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

          val user = requestExecutor.executeSuspend(GithubApiRequests.CurrentUser.get(account.server))
          val names = GithubApiPagesLoader.loadAll(requestExecutor, GithubApiRequests.CurrentUser.Repos.pages(account.server, type = Type.OWNER))
            .mapSmartSet { it.name }

          user.canCreatePrivateRepo() to names
        }
      }
      catch (mte: GithubMissingTokenException) {
        GHAccountsUtil.requestNewToken(account, project, comp) ?: throw mte
      }
    }
  }

  fun performShareProjectOnGithub(
    gitRepository: GitRepository?,
    root: VirtualFile,
    shareDialogResult: GithubShareDialog.Result,
  ) {
    val name: String = shareDialogResult.repositoryName
    val remoteName: String = shareDialogResult.remoteName
    val account: GithubAccount = shareDialogResult.account!!

    val token = GHCompatibilityUtil.getOrRequestToken(account, project) ?: return
    val requestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

    cs.launch(Dispatchers.Default) {
      withBackgroundProgress(project, GithubBundle.message("share.process"), false) {
        try {
          reportSequentialProgress(size = 7) { reporter ->
            // create GitHub repo (network)
            val url = reporter.itemStep(GithubBundle.message("share.process.creating.repository")) {
              LOG.info("Creating GitHub repository")
              createRepo(requestExecutor, shareDialogResult)
            }
            LOG.info("Successfully created GitHub repository")

            // creating empty git repo if git is not initialized
            val repository = reporter.itemStep(GithubBundle.message("share.process.creating.git.repository")) {
              ensureGitRepositoryExistsAndGet(gitRepository, root)
            }
            if (repository == null) return@withBackgroundProgress

            // retrieve remote URL
            val remoteUrl = reporter.itemStep(GithubBundle.message("share.process.retrieving.username")) {
              retrieveRepoRemoteUrl(requestExecutor, account, name)
            }

            // git remote add origin git@github.com:login/name.git
            reporter.itemStep(GithubBundle.message("share.process.adding.gh.as.remote.host")) {
              LOG.info("Adding GitHub as a remote host")
              addGitRemote(repository, remoteName, remoteUrl)
            }

            // create sample commit for binding project
            if (!performFirstCommitIfRequired(project, root, repository, reporter, name, url)) {
              return@withBackgroundProgress
            }

            // git push origin master
            if (!reporter.itemStep(GithubBundle.message("share.process.pushing.to.github.master", repository.currentBranch?.name ?: "")) {
                LOG.info("Pushing to github master")
                pushCurrentBranch(project, repository, remoteName, remoteUrl, name, url)
              }) {
              return@withBackgroundProgress
            }

            // Show final success notification
            GithubNotifications.showInfoURL(
              project,
              GithubNotificationIdsHolder.SHARE_PROJECT_SUCCESSFULLY_SHARED,
              GithubBundle.message("share.process.successfully.shared"),
              name,
              url
            )
          }
        }
        catch (error: Throwable) {
          GithubNotifications.showError(
            project,
            GithubNotificationIdsHolder.SHARE_CANNOT_CREATE_REPO,
            GithubBundle.message("share.error.failed.to.create.repo"), error
          )
        }
      }
    }
  }

  // Service
  private suspend fun createRepo(
    requestExecutor: GithubApiRequestExecutor,
    shareDialogResult: GithubShareDialog.Result,
  ): String {
    val name: String = shareDialogResult.repositoryName
    val description: String = shareDialogResult.description
    val isPrivate: Boolean = shareDialogResult.isPrivate
    val account: GithubAccount = shareDialogResult.account!!

    return requestExecutor.executeSuspend(GithubApiRequests.CurrentUser.Repos.create(account.server, name, description, isPrivate)).htmlUrl
  }

  // Service
  private suspend fun retrieveRepoRemoteUrl(
    requestExecutor: GithubApiRequestExecutor,
    account: GithubAccount,
    name: String,
  ): String {
    val username = GHCachingAccountInformationProvider.getInstance().loadInformation(requestExecutor, account).login
    return GithubGitHelper.getInstance().getRemoteUrl(account.server, username, name)
  }

  // Git/collab
  private fun addGitRemote(
    repository: GitRepository,
    remoteName: String,
    remoteUrl: String,
  ) {
    Git.getInstance().addRemote(repository, remoteName, remoteUrl).throwOnError()
    repository.update()
  }

  // Git/collab + Services notifications
  private fun ensureGitRepositoryExistsAndGet(
    repository: GitRepository?,
    root: VirtualFile,
  ): GitRepository? {
    LOG.info("Binding local project with GitHub")
    if (repository == null) {
      LOG.info("No git detected, creating empty git repo")
      if (!createEmptyGitRepository(project, root)) {
        return null
      }
    }

    val repositoryManager = GitUtil.getRepositoryManager(project)
    val repository = repositoryManager.getRepositoryForRoot(root)
    if (repository == null) {
      GithubNotifications.showError(
        project,
        GithubNotificationIdsHolder.SHARE_CANNOT_FIND_GIT_REPO,
        GithubBundle.message("share.error.failed.to.create.repo"),
        GithubBundle.message("cannot.find.git.repo")
      )
      return null
    }

    return repository
  }

  // Git/collab
  private fun createEmptyGitRepository(
    project: Project,
    root: VirtualFile,
  ): Boolean {
    val result = Git.getInstance().init(project, root)
    if (!result.success()) {
      VcsNotifier.getInstance(project).notifyError(
        GithubNotificationIdsHolder.GIT_REPO_INIT_REPO,
        GitBundle.message("initializing.title"),
        result.errorOutputAsHtmlString
      )
      LOG.info("Failed to create empty git repo: " + result.errorOutputAsJoinedString)
      return false
    }
    GitInit.refreshAndConfigureVcsMappings(project, root, root.path)
    GitUtil.generateGitignoreFileIfNeeded(project, root)
    return true
  }

  // Git/collab + Service notifications
  private suspend fun performFirstCommitIfRequired(
    project: Project,
    root: VirtualFile,
    repository: GitRepository,
    reporter: SequentialProgressReporter,
    name: @NlsSafe String,
    url: String,
  ): Boolean {
    // check if there is no commits
    if (!repository.isFresh) {
      return true
    }

    withModalProgress(project, IdeBundle.message("progress.saving.project", project.name)) {
      saveSettings(project, forceSavingAllSettings = true)
    }

    LOG.info("Trying to commit")
    try {
      LOG.info("Adding files for commit")

      // ask for files to add
      val addFilesStepResult = reporter.itemStep(GithubBundle.message("share.process.adding.files")) {
        val manager = GitUtil.getRepositoryManager(project)
        val trackedFiles = ChangeListManager.getInstance(project).affectedFiles.filter {
          it.isInLocalFileSystem && manager.getRepositoryForFileQuick(it) == repository
        }.toMutableList()
        val untrackedFiles =
          filterOutIgnored(project, repository.untrackedFilesHolder.retrieveUntrackedFilePaths().mapNotNull(FilePath::getVirtualFile))
        trackedFiles.removeAll(untrackedFiles) // fix IDEA-119855

        val allFiles = ArrayList<VirtualFile>()
        allFiles.addAll(trackedFiles)
        allFiles.addAll(untrackedFiles)

        val data = withContext(Dispatchers.EDT) {
          val dialog = GithubUntrackedFilesDialog(project, allFiles).apply {
            if (!trackedFiles.isEmpty()) {
              selectedFiles = trackedFiles
            }
            DialogManager.show(this)
          }

          if (dialog.isOK && dialog.selectedFiles.isNotEmpty()) {
            dialog.selectedFiles to dialog.commitMessage
          }
          else {
            null
          }
        }


        if (data == null) {
          GithubNotifications.showInfoURL(
            project,
            GithubNotificationIdsHolder.SHARE_EMPTY_REPO_CREATED,
            GithubBundle.message("share.process.empty.project.created"),
            name, url
          )
          return@itemStep null
        }
        val (files2commit, commitMessage) = data

        val files2add = ContainerUtil.intersection(untrackedFiles, files2commit)
        val files2rm = ContainerUtil.subtract(trackedFiles, files2commit)
        val modified = HashSet(trackedFiles)
        modified.addAll(files2commit)

        GitFileUtils.addFiles(project, root, files2add)
        GitFileUtils.deleteFilesFromCache(project, root, files2rm)

        modified to commitMessage
      }
      if (addFilesStepResult == null) return false
      val (modified, commitMessage) = addFilesStepResult

      // commit
      reporter.itemStep(GithubBundle.message("share.process.performing.commit")) {
        val handler = GitLineHandler(project, root, GitCommand.COMMIT)
        handler.setStdoutSuppressed(false)
        handler.addParameters("-m", commitMessage)
        handler.endOptions()
        Git.getInstance().runCommand(handler).throwOnError()

        VcsFileUtil.markFilesDirty(project, modified)
      }
    }
    catch (e: VcsException) {
      LOG.warn(e)
      GithubNotifications.showErrorURL(
        project, GithubNotificationIdsHolder.SHARE_PROJECT_INIT_COMMIT_FAILED,
        GithubBundle.message("share.error.cannot.finish"),
        GithubBundle.message("share.error.created.project"),
        " '$name' ",
        GithubBundle.message("share.error.init.commit.failed") + GithubUtil.getErrorTextFromException(
          e),
        url
      )
      return false
    }

    LOG.info("Successfully created initial commit")
    return true
  }

  // Git/collab
  private fun filterOutIgnored(project: Project, files: Collection<VirtualFile>): Collection<VirtualFile> {
    val changeListManager = ChangeListManager.getInstance(project)
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    return files.filter({ file -> !changeListManager.isIgnoredFile(file) && !vcsManager.isIgnored(file) })
  }

  // Git/collab + Service notifications
  private fun pushCurrentBranch(
    project: Project,
    repository: GitRepository,
    remoteName: String,
    remoteUrl: String,
    name: String,
    url: String,
  ): Boolean {
    val currentBranch = repository.currentBranch
    if (currentBranch == null) {
      GithubNotifications.showErrorURL(
        project, GithubNotificationIdsHolder.SHARE_PROJECT_INIT_PUSH_FAILED,
        GithubBundle.message("share.error.cannot.finish"),
        GithubBundle.message("share.error.created.project"),
        " '$name' ",
        GithubBundle.message("share.error.push.no.current.branch"),
        url
      )
      return false
    }
    val result = Git.getInstance().push(repository, remoteName, remoteUrl, currentBranch.name, true)
    if (!result.success()) {
      GithubNotifications.showErrorURL(
        project, GithubNotificationIdsHolder.SHARE_PROJECT_INIT_PUSH_FAILED,
        GithubBundle.message("share.error.cannot.finish"),
        GithubBundle.message("share.error.created.project"),
        " '$name' ",
        GithubBundle.message("share.error.push.failed", result.errorOutputAsHtmlString),
        url
      )
      return false
    }
    return true
  }
}