// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
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
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.request.Type
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GHCachingAccountInformationProvider
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.GithubShareDialog
import org.jetbrains.plugins.github.ui.dialog.GithubExistingRemotesDialog
import org.jetbrains.plugins.github.ui.dialog.GithubUntrackedFilesDialog
import org.jetbrains.plugins.github.util.*
import java.awt.Component
import java.io.IOException

object GHShareProjectUtil {
  private val LOG = GithubUtil.LOG

  @JvmStatic
  fun shareProjectOnGithub(project: Project, file: VirtualFile?) {
    val gitRepository = GithubGitHelper.findGitRepository(project, file)
    val root = gitRepository?.root ?: project.baseDir
    shareProjectOnGithub(project, gitRepository, root, project.name)
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
  fun shareProjectOnGithub(project: Project,
                           gitRepository: GitRepository?,
                           root: VirtualFile,
                           projectName: @NlsSafe String) {
    FileDocumentManager.getInstance().saveAllDocuments()
    runWithModalProgressBlocking(project, IdeBundle.message("progress.saving.project", project.name)) {
      saveSettings(project)
    }

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

    val progressManager = service<ProgressManager>()
    val accountInformationProvider = GHCachingAccountInformationProvider.getInstance()
    val gitHelper = GithubGitHelper.getInstance()
    val git = Git.getInstance()

    val accountInformationLoader = object : (GithubAccount, Component) -> Pair<Boolean, Set<String>> {
      private val loadedInfo = mutableMapOf<GithubAccount, Pair<Boolean, Set<String>>>()

      @Throws(IOException::class)
      override fun invoke(account: GithubAccount, comp: Component) = loadedInfo.getOrPut(account) {
        loadEnsuringTokenExistsToken(account, comp)
      }

      private fun loadEnsuringTokenExistsToken(account: GithubAccount, comp: Component): Pair<Boolean, Set<String>> {
        while (true) {
          try {
            return progressManager.runProcessWithProgressSynchronously(ThrowableComputable<Pair<Boolean, Set<String>>, IOException> {
              val token = runBlockingCancellable {
                service<GHAccountManager>().findCredentials(account) ?: throw GithubMissingTokenException(account)
              }
              val requestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

              val user = requestExecutor.execute(progressManager.progressIndicator, GithubApiRequests.CurrentUser.get(account.server))
              val names = GithubApiPagesLoader
                .loadAll(requestExecutor, progressManager.progressIndicator,
                         GithubApiRequests.CurrentUser.Repos.pages(account.server, type = Type.OWNER))
                .mapSmartSet { it.name }
              user.canCreatePrivateRepo() to names
            }, GithubBundle.message("share.process.loading.account.info", account), true, project)
          }
          catch (mte: GithubMissingTokenException) {
            GHAccountsUtil.requestNewToken(account, project, comp) ?: throw mte
          }
        }
      }
    }

    val shareDialog = GithubShareDialog(project,
                                        gitRepository?.remotes?.map { it.name }?.toSet() ?: emptySet(),
                                        accountInformationLoader,
                                        projectName)
    DialogManager.show(shareDialog)
    if (!shareDialog.isOK) {
      return
    }

    val name: String = shareDialog.getRepositoryName()
    val isPrivate: Boolean = shareDialog.isPrivate()
    val remoteName: String = shareDialog.getRemoteName()
    val description: String = shareDialog.getDescription()
    val account: GithubAccount = shareDialog.getAccount()!!

    object : Task.Backgroundable(project, GithubBundle.message("share.process")) {
      private lateinit var url: String

      override fun run(indicator: ProgressIndicator) {
        val token = GHCompatibilityUtil.getOrRequestToken(account, project) ?: return

        val requestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)

        // create GitHub repo (network)
        LOG.info("Creating GitHub repository")
        indicator.text = GithubBundle.message("share.process.creating.repository")
        url = requestExecutor
          .execute(indicator, GithubApiRequests.CurrentUser.Repos.create(account.server, name, description, isPrivate)).htmlUrl
        LOG.info("Successfully created GitHub repository")

        // creating empty git repo if git is not initialized
        LOG.info("Binding local project with GitHub")
        if (gitRepository == null) {
          LOG.info("No git detected, creating empty git repo")
          indicator.text = GithubBundle.message("share.process.creating.git.repository")
          if (!createEmptyGitRepository(project, root)) {
            return
          }
        }

        val repositoryManager = GitUtil.getRepositoryManager(project)
        val repository = repositoryManager.getRepositoryForRoot(root)
        if (repository == null) {
          GithubNotifications.showError(project,
                                        GithubNotificationIdsHolder.SHARE_CANNOT_FIND_GIT_REPO,
                                        GithubBundle.message("share.error.failed.to.create.repo"),
                                        GithubBundle.message("cannot.find.git.repo"))
          return
        }

        indicator.text = GithubBundle.message("share.process.retrieving.username")
        val username = runBlockingCancellable {
          accountInformationProvider.loadInformation(requestExecutor, account)
        }.login
        val remoteUrl = gitHelper.getRemoteUrl(account.server, username, name)

        //git remote add origin git@github.com:login/name.git
        LOG.info("Adding GitHub as a remote host")
        indicator.text = GithubBundle.message("share.process.adding.gh.as.remote.host")
        git.addRemote(repository, remoteName, remoteUrl).throwOnError()
        repository.update()

        // create sample commit for binding project
        if (!performFirstCommitIfRequired(project, root, repository, indicator, name, url)) {
          return
        }

        //git push origin master
        LOG.info("Pushing to github master")
        indicator.text = GithubBundle.message("share.process.pushing.to.github.master", repository.currentBranch?.name ?: "")
        if (!pushCurrentBranch(project, repository, remoteName, remoteUrl, name, url)) {
          return
        }

        GithubNotifications.showInfoURL(project,
                                        GithubNotificationIdsHolder.SHARE_PROJECT_SUCCESSFULLY_SHARED,
                                        GithubBundle.message("share.process.successfully.shared"),
                                        name,
                                        url)
      }

      private fun createEmptyGitRepository(project: Project,
                                           root: VirtualFile): Boolean {
        val result = Git.getInstance().init(project, root)
        if (!result.success()) {
          VcsNotifier.getInstance(project).notifyError(GithubNotificationIdsHolder.GIT_REPO_INIT_REPO,
                                                       GitBundle.message("initializing.title"),
                                                       result.errorOutputAsHtmlString)
          LOG.info("Failed to create empty git repo: " + result.errorOutputAsJoinedString)
          return false
        }
        GitInit.refreshAndConfigureVcsMappings(project, root, root.path)
        GitUtil.generateGitignoreFileIfNeeded(project, root)
        return true
      }

      private fun performFirstCommitIfRequired(project: Project,
                                               root: VirtualFile,
                                               repository: GitRepository,
                                               indicator: ProgressIndicator,
                                               name: @NlsSafe String,
                                               url: String): Boolean {
        // check if there is no commits
        if (!repository.isFresh) {
          return true
        }

        LOG.info("Trying to commit")
        try {
          LOG.info("Adding files for commit")
          indicator.text = GithubBundle.message("share.process.adding.files")

          // ask for files to add
          val trackedFiles = ChangeListManager.getInstance(project).affectedFiles.toMutableList()
          val untrackedFiles =
            filterOutIgnored(project, repository.untrackedFilesHolder.retrieveUntrackedFilePaths().mapNotNull(FilePath::getVirtualFile))
          trackedFiles.removeAll(untrackedFiles) // fix IDEA-119855

          val allFiles = ArrayList<VirtualFile>()
          allFiles.addAll(trackedFiles)
          allFiles.addAll(untrackedFiles)

          val data = invokeAndWaitIfNeeded(indicator.modalityState) {
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
            GithubNotifications.showInfoURL(project,
                                            GithubNotificationIdsHolder.SHARE_EMPTY_REPO_CREATED,
                                            GithubBundle.message("share.process.empty.project.created"),
                                            name, url)
            return false
          }
          val (files2commit, commitMessage) = data

          val files2add = ContainerUtil.intersection(untrackedFiles, files2commit)
          val files2rm = ContainerUtil.subtract(trackedFiles, files2commit)
          val modified = HashSet(trackedFiles)
          modified.addAll(files2commit)

          GitFileUtils.addFiles(project, root, files2add)
          GitFileUtils.deleteFilesFromCache(project, root, files2rm)

          // commit
          LOG.info("Performing commit")
          indicator.text = GithubBundle.message("share.process.performing.commit")
          val handler = GitLineHandler(project, root, GitCommand.COMMIT)
          handler.setStdoutSuppressed(false)
          handler.addParameters("-m", commitMessage)
          handler.endOptions()
          Git.getInstance().runCommand(handler).throwOnError()

          VcsFileUtil.markFilesDirty(project, modified)
        }
        catch (e: VcsException) {
          LOG.warn(e)
          GithubNotifications.showErrorURL(project, GithubNotificationIdsHolder.SHARE_PROJECT_INIT_COMMIT_FAILED,
                                           GithubBundle.message("share.error.cannot.finish"),
                                           GithubBundle.message("share.error.created.project"),
                                           " '$name' ",
                                           GithubBundle.message("share.error.init.commit.failed") + GithubUtil.getErrorTextFromException(
                                             e),
                                           url)
          return false
        }

        LOG.info("Successfully created initial commit")
        return true
      }

      private fun filterOutIgnored(project: Project, files: Collection<VirtualFile>): Collection<VirtualFile> {
        val changeListManager = ChangeListManager.getInstance(project)
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        return ContainerUtil.filter(files) { file -> !changeListManager.isIgnoredFile(file) && !vcsManager.isIgnored(file) }
      }

      private fun pushCurrentBranch(project: Project,
                                    repository: GitRepository,
                                    remoteName: String,
                                    remoteUrl: String,
                                    name: String,
                                    url: String): Boolean {
        val currentBranch = repository.currentBranch
        if (currentBranch == null) {
          GithubNotifications.showErrorURL(project, GithubNotificationIdsHolder.SHARE_PROJECT_INIT_PUSH_FAILED,
                                           GithubBundle.message("share.error.cannot.finish"),
                                           GithubBundle.message("share.error.created.project"),
                                           " '$name' ",
                                           GithubBundle.message("share.error.push.no.current.branch"),
                                           url)
          return false
        }
        val result = git.push(repository, remoteName, remoteUrl, currentBranch.name, true)
        if (!result.success()) {
          GithubNotifications.showErrorURL(project, GithubNotificationIdsHolder.SHARE_PROJECT_INIT_PUSH_FAILED,
                                           GithubBundle.message("share.error.cannot.finish"),
                                           GithubBundle.message("share.error.created.project"),
                                           " '$name' ",
                                           GithubBundle.message("share.error.push.failed", result.errorOutputAsHtmlString),
                                           url)
          return false
        }
        return true
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project,
                                      GithubNotificationIdsHolder.SHARE_CANNOT_CREATE_REPO,
                                      GithubBundle.message("share.error.failed.to.create.repo"), error)
      }
    }.queue()
  }
}