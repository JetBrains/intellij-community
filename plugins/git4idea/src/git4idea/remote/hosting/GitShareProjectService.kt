// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.commit.CommitNotification
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.actions.GitInit
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.ui.ShareProjectExistingRemotesDialog
import git4idea.remote.hosting.ui.ShareProjectUntrackedFilesDialog
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.net.UnknownHostException

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitShareProjectService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  companion object {
    private val LOG = logger<GitShareProjectService>()
  }

  /**
   * @return `true` only when the user has chosen to continue with sharing the project.
   */
  fun showExistingRemotesDialog(
    hostServiceName: @NlsContexts.ConfigurableName String,
    gitRepository: GitRepository?,
    knownRepositories: Set<HostedGitRepositoryMapping>,
  ): Boolean {
    val possibleRemotes = gitRepository
      ?.let { repository -> knownRepositories.filter { it.remote.repository == repository } }
      ?.map { it.remote.url }.orEmpty()

    if (possibleRemotes.isNotEmpty()) {
      val existingRemotesDialog = ShareProjectExistingRemotesDialog(project, hostServiceName, possibleRemotes)
      DialogManager.show(existingRemotesDialog)
      return existingRemotesDialog.isOK
    }

    return true
  }

  // get gitRepository
  // check for existing git repo
  // check available repos and privateRepo access (net)
  // Show dialog (window)
  // create GitHub/Lab repo (net)
  // create local git repo (if not exist)
  // add GitHub/Lab as a remote host
  // make first commit
  // push everything (net)
  fun <RepoResult> performShareProject(
    hostServiceName: @NlsContexts.ConfigurableName String,
    gitRepository: GitRepository?,
    root: VirtualFile,
    repositoryName: @Nls String,
    remoteName: String,
    createRepo: suspend () -> RepoResult,
    extractRepoWebUrl: (RepoResult) -> String,
    extractRepoRemoteUrl: (RepoResult) -> String,
  ) {
    cs.launch(Dispatchers.Default) {
      withBackgroundProgress(project, GitBundle.message("share.process", hostServiceName), cancellable = true) {
        try {
          reportSequentialProgress(size = 7) { reporter ->
            // create GitHub/Lab repo (network)
            val repoResult = reporter.itemStep(GitBundle.message("share.process.creating.repository", hostServiceName)) {
              LOG.info("Creating ${hostServiceName} repository")
              createRepo()
            }
            val url = extractRepoWebUrl(repoResult)
            LOG.info("Successfully created ${hostServiceName} repository")

            // creating empty git repo if git is not initialized
            val repository = reporter.itemStep(GitBundle.message("share.process.creating.git.repository")) {
              coroutineToIndicator { ensureGitRepositoryExistsAndGet(hostServiceName, gitRepository, root) }
            }
            if (repository == null) return@withBackgroundProgress

            // retrieve remote URL
            val remoteUrl = reporter.itemStep(GitBundle.message("share.process.retrieving.username")) {
              extractRepoRemoteUrl(repoResult)
            }

            // git remote add origin git@github.com:login/name.git
            reporter.itemStep(GitBundle.message("share.process.adding.gh.as.remote.host", hostServiceName)) {
              LOG.info("Adding ${hostServiceName} as a remote host")
              coroutineToIndicator { addGitRemote(repository, remoteName, remoteUrl) }
            }

            // create sample commit for binding project
            if (!performFirstCommitIfRequired(hostServiceName, root, repository, reporter, repositoryName, url)) {
              return@withBackgroundProgress
            }

            // git push origin master
            if (!reporter.itemStep(
                GitBundle.message("share.process.pushing.to.host.master", hostServiceName, repository.currentBranch?.name ?: "")) {
                LOG.info("Pushing to ${hostServiceName} master")
                coroutineToIndicator {
                  pushCurrentBranch(hostServiceName, repository, remoteName, remoteUrl, repositoryName, url)
                }
              }
            ) {
              return@withBackgroundProgress
            }

            // Show final success notification
            VcsNotifier.getInstance(project).notifyImportantInfo(
              VcsNotificationIdsHolder.SHARE_PROJECT_SUCCESSFULLY_SHARED,
              GitBundle.message("share.process.successfully.shared", hostServiceName),
              HtmlChunk.link(url, repositoryName).toString(),
              NotificationListener.URL_OPENING_LISTENER
            )

            // Remove project sharing notification
            NotificationsManager.getNotificationsManager()
              .getNotificationsOfType(CommitNotification::class.java, project)
              .forEach { it.expire() }
          }
        }
        catch (error: Throwable) {
          if (error is ProcessCanceledException) return@withBackgroundProgress
          VcsNotifier.getInstance(project).notifyError(
            VcsNotificationIdsHolder.SHARE_CANNOT_CREATE_REPO,
            GitBundle.message("share.error.failed.to.create.repo"),
            getErrorTextFromException(error)
          )
        }
      }
    }
  }

  private fun getErrorTextFromException(e: Throwable): @Nls String =
    if (e is UnknownHostException) GitBundle.message("share.error.unknownHost", e.message)
    else StringUtil.notNullize(e.message, GitBundle.message("share.error.unknown"))

  private fun addGitRemote(
    repository: GitRepository,
    remoteName: String,
    remoteUrl: String,
  ) {
    Git.getInstance().addRemote(repository, remoteName, remoteUrl).throwOnError()
    repository.update()
  }

  private fun ensureGitRepositoryExistsAndGet(
    hostServiceName: @NlsContexts.ConfigurableName String,
    repository: GitRepository?,
    root: VirtualFile,
  ): GitRepository? {
    LOG.info("Binding local project with ${hostServiceName}")
    if (repository == null) {
      LOG.info("No git detected, creating empty git repo")
      if (!createEmptyGitRepository(root)) {
        return null
      }
    }

    val repositoryManager = GitUtil.getRepositoryManager(project)
    val repository = repositoryManager.getRepositoryForRoot(root)
    if (repository == null) {
      VcsNotifier.getInstance(project).notifyError(
        VcsNotificationIdsHolder.SHARE_CANNOT_FIND_GIT_REPO,
        GitBundle.message("share.error.failed.to.create.repo"),
        GitBundle.message("share.error.cannot.find.git.repo")
      )
      return null
    }

    return repository
  }

  private fun createEmptyGitRepository(
    root: VirtualFile,
  ): Boolean {
    val result = Git.getInstance().init(project, root)
    if (!result.success()) {
      VcsNotifier.getInstance(project).notifyError(
        VcsNotificationIdsHolder.GIT_REPO_INIT_REPO,
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

  private suspend fun performFirstCommitIfRequired(
    hostServiceName: @NlsContexts.ConfigurableName String,
    root: VirtualFile,
    repository: GitRepository,
    reporter: SequentialProgressReporter,
    name: @NlsSafe String,
    url: String,
  ): Boolean {
    // check if there is no commits
    if (!repository.isFresh) return true

    withModalProgress(project, IdeBundle.message("progress.saving.project", project.name)) {
      saveSettings(project, forceSavingAllSettings = true)
    }

    LOG.info("Trying to commit")
    try {
      LOG.info("Adding files for commit")

      // ask for files to add
      val addFilesStepResult = reporter.itemStep(GitBundle.message("share.process.adding.files")) {
        val manager = GitUtil.getRepositoryManager(project)
        val trackedFiles = ChangeListManager.getInstance(project).affectedFiles.filter {
          it.isInLocalFileSystem && manager.getRepositoryForFileQuick(it) == repository
        }.toMutableList()
        val untrackedFiles =
          filterOutIgnored(repository.untrackedFilesHolder.retrieveUntrackedFilePaths().mapNotNull(FilePath::getVirtualFile))
        trackedFiles.removeAll(untrackedFiles) // fix IDEA-119855

        val allFiles = ArrayList<VirtualFile>()
        allFiles.addAll(trackedFiles)
        allFiles.addAll(untrackedFiles)

        val data = withContext(Dispatchers.EDT) {
          val dialog = ShareProjectUntrackedFilesDialog(project, allFiles).apply {
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
          VcsNotifier.getInstance(project).notifyImportantInfo(
            VcsNotificationIdsHolder.SHARE_EMPTY_REPO_CREATED,
            GitBundle.message("share.process.empty.project.created", hostServiceName),
            HtmlChunk.link(url, name).toString(),
            NotificationListener.URL_OPENING_LISTENER
          )
          return@itemStep null
        }
        val (files2commit, commitMessage) = data

        val files2add = ContainerUtil.intersection(untrackedFiles, files2commit)
        val files2rm = ContainerUtil.subtract(trackedFiles, files2commit)
        val modified = HashSet(trackedFiles)
        modified.addAll(files2commit)

        coroutineToIndicator {
          GitFileUtils.addFiles(project, root, files2add)
          GitFileUtils.deleteFilesFromCache(project, root, files2rm)

          modified to commitMessage
        }
      }
      if (addFilesStepResult == null) return false
      val (modified, commitMessage) = addFilesStepResult

      // commit
      reporter.itemStep(GitBundle.message("share.process.performing.commit")) {
        val handler = GitLineHandler(project, root, GitCommand.COMMIT)
        handler.setStdoutSuppressed(false)
        handler.addParameters("-m", commitMessage)
        handler.endOptions()

        coroutineToIndicator {
          Git.getInstance().runCommand(handler).throwOnError()
          VcsFileUtil.markFilesDirty(project, modified)
        }
      }
    }
    catch (e: VcsException) {
      LOG.warn(e)
      notifyProjectCreationFailure(hostServiceName, VcsNotificationIdsHolder.SHARE_PROJECT_INIT_COMMIT_FAILED, name, url,
                                   GitBundle.message("share.error.init.commit.failed", hostServiceName) + getErrorTextFromException(e))
      return false
    }

    LOG.info("Successfully created initial commit")
    return true
  }

  private fun filterOutIgnored(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val changeListManager = ChangeListManager.getInstance(project)
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    return files.filter({ file -> !changeListManager.isIgnoredFile(file) && !vcsManager.isIgnored(file) })
  }

  private fun pushCurrentBranch(
    hostServiceName: @NlsContexts.ConfigurableName String,
    repository: GitRepository,
    remoteName: String,
    remoteUrl: String,
    name: @Nls String,
    url: String,
  ): Boolean {
    val currentBranch = repository.currentBranch
    if (currentBranch == null) {
      notifyProjectCreationFailure(hostServiceName, VcsNotificationIdsHolder.SHARE_PROJECT_INIT_PUSH_FAILED, name, url,
                                   GitBundle.message("share.error.push.no.current.branch", hostServiceName))
      return false
    }
    val result = Git.getInstance().push(repository, remoteName, remoteUrl, currentBranch.name, true)
    if (!result.success()) {
      notifyProjectCreationFailure(hostServiceName, VcsNotificationIdsHolder.SHARE_PROJECT_INIT_PUSH_FAILED, name, url,
                                   GitBundle.message("share.error.push.failed", hostServiceName, result.errorOutputAsHtmlString))
      return false
    }
    return true
  }

  private fun notifyProjectCreationFailure(
    hostServiceName: @NlsContexts.ConfigurableName String,
    notificationId: String,
    name: @Nls String,
    url: String,
    postfix: @Nls String,
  ) {
    VcsNotifier.getInstance(project).notifyError(
      notificationId,
      GitBundle.message("share.error.cannot.finish", hostServiceName),
      "${GitBundle.message("share.error.created.project")}${HtmlChunk.link(url, " '$name' ")}${postfix}",
      NotificationListener.URL_OPENING_LISTENER
    )
  }
}