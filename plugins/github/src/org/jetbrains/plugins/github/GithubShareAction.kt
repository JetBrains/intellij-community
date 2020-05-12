// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.mapSmartSet
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.actions.GitInit
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.request.Type
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.GithubShareDialog
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.io.IOException
import java.util.*
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class GithubShareAction : DumbAwareAction(GithubBundle.messagePointer("share.action"),
                                          GithubBundle.messagePointer("share.action.description"),
                                          AllIcons.Vcs.Vendors.Github) {
  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    e.presentation.isEnabledAndVisible = project != null && !project.isDefault
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
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

    if (project == null || project.isDisposed) {
      return
    }

    shareProjectOnGithub(project, file)
  }

  companion object {
    private val LOG = GithubUtil.LOG

    @JvmStatic
    fun shareProjectOnGithub(project: Project, file: VirtualFile?) {
      FileDocumentManager.getInstance().saveAllDocuments();

      val gitRepository = GithubGitHelper.findGitRepository(project, file)

      if (!service<GithubAccountsMigrationHelper>().migrate(project)) return
      val authManager = service<GithubAuthenticationManager>()
      if (!authManager.ensureHasAccounts(project)) return
      val accounts = authManager.getAccounts()

      val progressManager = service<ProgressManager>()
      val requestExecutorManager = service<GithubApiRequestExecutorManager>()
      val accountInformationProvider = service<GithubAccountInformationProvider>()
      val gitHelper = service<GithubGitHelper>()
      val git = service<Git>()

      val possibleRemotes = gitRepository?.let(gitHelper::getAccessibleRemoteUrls).orEmpty()
      if (possibleRemotes.isNotEmpty()) {
        val existingRemotesDialog = GithubExistingRemotesDialog(project, possibleRemotes)
        DialogManager.show(existingRemotesDialog)
        if (!existingRemotesDialog.isOK) {
          return
        }
      }

      val accountInformationLoader = object : (GithubAccount, Component) -> Pair<Boolean, Set<String>> {
        private val loadedInfo = mutableMapOf<GithubAccount, Pair<Boolean, Set<String>>>()

        @Throws(IOException::class)
        override fun invoke(account: GithubAccount, parentComponent: Component) = loadedInfo.getOrPut(account) {
          val requestExecutor = requestExecutorManager.getExecutor(account, parentComponent) ?: throw ProcessCanceledException()
          progressManager.runProcessWithProgressSynchronously(ThrowableComputable<Pair<Boolean, Set<String>>, IOException> {

            val user = requestExecutor.execute(progressManager.progressIndicator, GithubApiRequests.CurrentUser.get(account.server))
            val names = GithubApiPagesLoader
              .loadAll(requestExecutor, progressManager.progressIndicator,
                       GithubApiRequests.CurrentUser.Repos.pages(account.server, Type.OWNER))
              .mapSmartSet { it.name }
            user.canCreatePrivateRepo() to names
          }, GithubBundle.message("share.process.loading.account.info", account), true, project)
        }
      }

      val shareDialog = GithubShareDialog(project,
                                          accounts,
                                          authManager.getDefaultAccount(project),
                                          gitRepository?.remotes?.map { it.name }?.toSet() ?: emptySet(),
                                          accountInformationLoader)
      DialogManager.show(shareDialog)
      if (!shareDialog.isOK) {
        return
      }

      val name: String = shareDialog.getRepositoryName()
      val isPrivate: Boolean = shareDialog.isPrivate()
      val remoteName: String = shareDialog.getRemoteName()
      val description: String = shareDialog.getDescription()
      val account: GithubAccount = shareDialog.getAccount()

      val requestExecutor = requestExecutorManager.getExecutor(account, project) ?: return
      object : Task.Backgroundable(project, GithubBundle.message("share.process")) {
        private lateinit var url: String

        override fun run(indicator: ProgressIndicator) {
          // create GitHub repo (network)
          LOG.info("Creating GitHub repository")
          indicator.text = GithubBundle.message("share.process.creating.repository")
          url = requestExecutor
            .execute(indicator, GithubApiRequests.CurrentUser.Repos.create(account.server, name, description, isPrivate)).htmlUrl
          LOG.info("Successfully created GitHub repository")

          val root = gitRepository?.root ?: project.baseDir
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
            GithubNotifications.showError(project, GithubBundle.message("share.error.failed.to.create.repo"),
                                          GithubBundle.message("cannot.find.git.repo"))
            return
          }

          indicator.text = GithubBundle.message("share.process.retrieving.username")
          val username = accountInformationProvider.getInformation(requestExecutor, indicator, account).login
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
          indicator.text = GithubBundle.message("share.process.pushing.to.github.master")
          if (!pushCurrentBranch(project, repository, remoteName, remoteUrl, name, url)) {
            return
          }

          GithubNotifications.showInfoURL(project, GithubBundle.message("share.process.successfully.shared"), name, url)
        }

        private fun createEmptyGitRepository(project: Project,
                                             root: VirtualFile): Boolean {
          val result = Git.getInstance().init(project, root)
          if (!result.success()) {
            VcsNotifier.getInstance(project).notifyError(GitBundle.getString("initializing.title"), result.errorOutputAsHtmlString)
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
                                                 name: String,
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
            val trackedFiles = ChangeListManager.getInstance(project).affectedFiles
            val untrackedFiles =
              filterOutIgnored(project, repository.untrackedFilesHolder.retrieveUntrackedFilePaths().mapNotNull(FilePath::getVirtualFile))
            trackedFiles.removeAll(untrackedFiles) // fix IDEA-119855

            val allFiles = ArrayList<VirtualFile>()
            allFiles.addAll(trackedFiles)
            allFiles.addAll(untrackedFiles)

            val dialog = invokeAndWaitIfNeeded(indicator.modalityState) {
              GithubUntrackedFilesDialog(project, allFiles).apply {
                if (!trackedFiles.isEmpty()) {
                  selectedFiles = trackedFiles
                }
                DialogManager.show(this)
              }
            }

            val files2commit = dialog.selectedFiles
            if (!dialog.isOK || files2commit.isEmpty()) {
              GithubNotifications.showInfoURL(project, GithubBundle.message("share.process.empty.project.created"), name, url)
              return false
            }

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
            handler.addParameters("-m", dialog.commitMessage)
            handler.endOptions()
            Git.getInstance().runCommand(handler).throwOnError()

            VcsFileUtil.markFilesDirty(project, modified)
          }
          catch (e: VcsException) {
            LOG.warn(e)
            GithubNotifications.showErrorURL(project, GithubBundle.message("share.error.cannot.finish"),
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
            GithubNotifications.showErrorURL(project, GithubBundle.message("share.error.cannot.finish"),
                                             GithubBundle.message("share.error.created.project"),
                                             " '$name' ",
                                             GithubBundle.message("share.error.push.no.current.branch"),
                                             url)
            return false
          }
          val result = git.push(repository, remoteName, remoteUrl, currentBranch.name, true)
          if (!result.success()) {
            GithubNotifications.showErrorURL(project, GithubBundle.message("share.error.cannot.finish"),
                                             GithubBundle.message("share.error.created.project"),
                                             " '$name' ",
                                             GithubBundle.message("share.error.push.failed", result.errorOutputAsHtmlString), url)
            return false
          }
          return true
        }

        override fun onThrowable(error: Throwable) {
          GithubNotifications.showError(project, GithubBundle.message("share.error.failed.to.create.repo"), error)
        }
      }.queue()
    }
  }

  @TestOnly
  class GithubExistingRemotesDialog(project: Project, private val remotes: List<String>) : DialogWrapper(project) {
    init {
      title = GithubBundle.message("share.error.project.is.on.github")
      setOKButtonText(GithubBundle.message("share.anyway.button"))
      init()
    }

    override fun createCenterPanel(): JComponent? {
      val mainText = JBLabel(if (remotes.size == 1) GithubBundle.message("share.action.remote.is.on.github")
                             else GithubBundle.message("share.action.remotes.are.on.github"))

      val remotesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
      }
      for (remote in remotes) {
        remotesPanel.add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
          add(LinkLabel.create(remote, Runnable { BrowserUtil.browse(remote) }))
          add(JBLabel(AllIcons.Ide.External_link_arrow))
        })
      }

      val messagesPanel = JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
        .addToTop(mainText)
        .addToCenter(remotesPanel)

      val iconContainer = Container().apply {
        layout = BorderLayout()
        add(JLabel(Messages.getQuestionIcon()), BorderLayout.NORTH)
      }
      return JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
        .addToCenter(messagesPanel)
        .addToLeft(iconContainer)
        .apply { border = JBUI.Borders.emptyBottom(UIUtil.LARGE_VGAP) }
    }
  }

  @TestOnly
  class GithubUntrackedFilesDialog(private val myProject: Project, untrackedFiles: List<VirtualFile>) :
    SelectFilesDialog(myProject, untrackedFiles, null, null, true, false),
    DataProvider {
    private var myCommitMessagePanel: CommitMessage? = null

    val commitMessage: String
      get() = myCommitMessagePanel!!.comment

    init {
      title = GithubBundle.message("untracked.files.dialog.title")
      setOKButtonText(CommonBundle.getAddButtonText())
      setCancelButtonText(CommonBundle.getCancelButtonText())
      init()
    }

    override fun createNorthPanel(): JComponent? {
      return null
    }

    override fun createCenterPanel(): JComponent? {
      val tree = super.createCenterPanel()

      val commitMessage = CommitMessage(myProject)
      Disposer.register(disposable, commitMessage)
      commitMessage.setCommitMessage("Initial commit")
      myCommitMessagePanel = commitMessage

      val splitter = Splitter(true)
      splitter.setHonorComponentsMinimumSize(true)
      splitter.firstComponent = tree
      splitter.secondComponent = myCommitMessagePanel
      splitter.proportion = 0.7f

      return splitter
    }

    override fun getData(@NonNls dataId: String): Any? {
      return if (VcsDataKeys.COMMIT_MESSAGE_CONTROL.`is`(dataId)) {
        myCommitMessagePanel
      }
      else null
    }

    override fun getDimensionServiceKey(): String? {
      return "Github.UntrackedFilesDialog"
    }
  }
}