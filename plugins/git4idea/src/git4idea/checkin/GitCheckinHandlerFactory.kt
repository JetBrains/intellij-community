// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.progressStep
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.vcs.log.VcsUser
import git4idea.GitUserRegistry
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.config.GitConfigUtil
import git4idea.config.GitVcsSettings
import git4idea.crlf.GitCrlfDialog
import git4idea.crlf.GitCrlfProblemsDetector
import git4idea.crlf.GitCrlfUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class GitCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey())

class GitUserNameCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitUserNameCheckinHandler(panel.project)
  }
}

class GitCRLFCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitCRLFCheckinHandler(panel.project)
  }
}

class GitDetachedRootCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitDetachedRootCheckinHandler(panel.project)
  }
}


private class GitCRLFCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override fun isEnabled(): Boolean {
    return GitVcsSettings.getInstance(project).warnAboutCrlf()
  }

  override suspend fun runGitCheck(commitInfo: CommitInfo): CommitProblem? {
    val git = Git.getInstance()

    val files = commitInfo.committedVirtualFiles // Deleted files aren't included. But for them, we don't care about CRLFs.
    val shouldWarn = withContext(Dispatchers.Default) {
      progressStep(endFraction = 1.0, GitBundle.message("progress.checking.line.separator.issues")) {
        withRawProgressReporter {
          coroutineToIndicator {
            GitCrlfProblemsDetector.detect(project, git, files).shouldWarn()
          }
        }
      }
    }
    if (!shouldWarn) return null

    val dialog = GitCrlfDialog(project)
    dialog.show()
    val decision = dialog.exitCode
    val dontWarnAgain = dialog.dontWarnAgain()

    if (decision == GitCrlfDialog.CANCEL) {
      return GitCRLFCommitProblem()
    }

    if (decision == GitCrlfDialog.SET) {
      val anyRoot = commitInfo.committedChanges.asSequence()
        .mapNotNull { ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(ChangesUtil.getFilePath(it)) }
        .filter { it.vcs is GitVcs }
        .map { it.path }
        .firstOrNull()
      if (anyRoot != null) {
        indeterminateStep(GitBundle.message("progress.setting.config.value")) {
          setCoreAutoCrlfAttribute(project, anyRoot)
        }
      }
    }
    else if (dontWarnAgain) {
      val settings = GitVcsSettings.getInstance(project)
      settings.setWarnAboutCrlf(false)
    }
    return null
  }

  private suspend fun setCoreAutoCrlfAttribute(project: Project, anyRoot: VirtualFile) {
    withContext(Dispatchers.IO) {
      withRawProgressReporter {
        coroutineToIndicator {
          try {
            GitConfigUtil.setValue(project, anyRoot, GitConfigUtil.CORE_AUTOCRLF, GitCrlfUtil.RECOMMENDED_VALUE, "--global")
          }
          catch (e: VcsException) {
            // it is not critical: the user just will get the dialog again next time
            logger<GitCRLFCheckinHandler>().warn("Couldn't globally set core.autocrlf in $anyRoot", e)
          }
        }
      }
    }
  }

  private class GitCRLFCommitProblem : CommitProblem {
    override val text: String get() = GitBundle.message("text.crlf.fix.notification.description.warning")

    override fun showModalSolution(project: Project, commitInfo: CommitInfo): ReturnResult {
      return ReturnResult.CANCEL // dialog was already shown
    }
  }
}

private class GitUserNameCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.LATE

  override fun isEnabled(): Boolean = true

  override suspend fun runGitCheck(commitInfo: CommitInfo): CommitProblem? {
    if (commitInfo.commitContext.commitAuthor != null) return null

    val vcs = GitVcs.getInstance(project)

    val affectedRoots = getSelectedRoots(commitInfo)
    val defined = getDefinedUserNames(project, affectedRoots, false)

    val allRoots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs).toMutableList()
    val notDefined = affectedRoots.toMutableList()
    notDefined.removeAll(defined.keys)

    if (notDefined.isEmpty()) return null

    // try to find a root with defined user name among other roots - to propose this user name in the dialog
    if (defined.isEmpty() && allRoots.size > affectedRoots.size) {
      allRoots.removeAll(affectedRoots)
      defined.putAll(getDefinedUserNames(project, allRoots, true))
    }

    val dialog = GitUserNameNotDefinedDialog(project, notDefined, affectedRoots, defined)
    if (!dialog.showAndGet()) return GitUserNameCommitProblem(closeWindow = true)

    val success = indeterminateStep(GitBundle.message("progress.setting.user.name.email")) {
      setUserNameUnderProgress(project, notDefined, dialog)
    }
    if (success) return null

    Messages.showErrorDialog(project, GitBundle.message("error.cant.set.user.name.email"), CommonBundle.getErrorTitle())
    return GitUserNameCommitProblem(closeWindow = false)
  }

  private suspend fun getDefinedUserNames(project: Project,
                                          roots: Collection<VirtualFile>,
                                          stopWhenFoundFirst: Boolean): MutableMap<VirtualFile, VcsUser> {
    return withContext(Dispatchers.Default) {
      withRawProgressReporter {
        coroutineToIndicator {
          val defined = HashMap<VirtualFile, VcsUser>()
          for (root in roots) {
            val user = GitUserRegistry.getInstance(project).readUser(root) ?: continue
            defined[root] = user
            if (stopWhenFoundFirst) {
              break
            }
          }
          defined
        }
      }
    }
  }

  private suspend fun setUserNameUnderProgress(project: Project,
                                               notDefined: Collection<VirtualFile>,
                                               dialog: GitUserNameNotDefinedDialog): Boolean {
    try {
      withContext(Dispatchers.IO) {
        withRawProgressReporter {
          coroutineToIndicator {
            if (dialog.isSetGlobalConfig) {
              GitConfigUtil.setValue(project, notDefined.iterator().next(), GitConfigUtil.USER_NAME, dialog.userName, "--global")
              GitConfigUtil.setValue(project, notDefined.iterator().next(), GitConfigUtil.USER_EMAIL, dialog.userEmail, "--global")
            }
            else {
              for (root in notDefined) {
                GitConfigUtil.setValue(project, root, GitConfigUtil.USER_NAME, dialog.userName)
                GitConfigUtil.setValue(project, root, GitConfigUtil.USER_EMAIL, dialog.userEmail)
              }
            }

          }
        }
      }
      return true
    }
    catch (e: VcsException) {
      logger<GitUserNameCheckinHandler>().warn("Couldn't set user.name and user.email", e)
      return false
    }
  }

  private class GitUserNameCommitProblem(val closeWindow: Boolean) : CommitProblem {
    override val text: String get() = GitBundle.message("commit.check.warning.user.name.email.not.set")

    override fun showModalSolution(project: Project, commitInfo: CommitInfo): ReturnResult {
      // dialog was already shown
      if (closeWindow) {
        return ReturnResult.CLOSE_WINDOW
      }
      else {
        return ReturnResult.CANCEL
      }
    }
  }
}

private class GitDetachedRootCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override fun isEnabled(): Boolean {
    return GitVcsSettings.getInstance(project).warnAboutDetachedHead()
  }

  override suspend fun runGitCheck(commitInfo: CommitInfo): CommitProblem? {
    val detachedRoot = getDetachedRoot(commitInfo)
    if (detachedRoot == null) return null

    if (detachedRoot.isDuringRebase) {
      return GitRebaseCommitProblem(detachedRoot.root)
    }
    else {
      return GitDetachedRootCommitProblem(detachedRoot.root)
    }
  }

  companion object {
    private const val DETACHED_HEAD_HELP_LINK = "https://git-scm.com/docs/git-checkout#_detached_head"
    private const val REBASE_HELP_LINK = "https://git-scm.com/docs/git-rebase"

    private fun detachedHeadDoNotAsk(project: Project): DoNotAskOption {
      return object : DoNotAskOption.Adapter() {
        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
          GitVcsSettings.getInstance(project).setWarnAboutDetachedHead(!isSelected)
        }

        override fun getDoNotShowMessage(): String {
          return GitBundle.message("checkbox.dont.warn.again")
        }
      }
    }
  }

  private class GitRebaseCommitProblem(val root: VirtualFile) : CommitProblemWithDetails {
    override val text: String
      get() = GitBundle.message("commit.check.warning.title.commit.during.rebase", root.presentableUrl)

    override fun showModalSolution(project: Project, commitInfo: CommitInfo): ReturnResult {
      val title = GitBundle.message("warning.title.commit.with.unfinished.rebase")
      val message = HtmlBuilder()
        .appendRaw(GitBundle.message("warning.message.commit.with.unfinished.rebase",
                                     HtmlChunk.text(root.presentableUrl).bold().toString()))
        .br()
        .appendLink(REBASE_HELP_LINK, GitBundle.message("link.label.commit.with.unfinished.rebase.read.more"))

      val commit = MessageDialogBuilder.okCancel(title, message.wrapWithHtmlBody().toString())
        .yesText(commitInfo.commitActionText)
        .icon(Messages.getWarningIcon())
        .doNotAsk(detachedHeadDoNotAsk(project))
        .ask(project)

      if (commit) {
        return ReturnResult.COMMIT
      }
      else {
        return ReturnResult.CLOSE_WINDOW
      }
    }

    override val showDetailsLink: String
      get() = GitBundle.message("commit.check.warning.title.commit.during.rebase.details")

    override val showDetailsAction: String
      get() = GitBundle.message("commit.check.warning.title.commit.during.rebase.details")

    override fun showDetails(project: Project) {
      BrowserUtil.browse(REBASE_HELP_LINK)
    }
  }

  private class GitDetachedRootCommitProblem(val root: VirtualFile) : CommitProblemWithDetails {
    override val text: String
      get() = GitBundle.message("commit.check.warning.title.commit.with.detached.head", root.presentableUrl)

    override fun showModalSolution(project: Project, commitInfo: CommitInfo): ReturnResult {
      val title = GitBundle.message("warning.title.commit.with.detached.head")
      val message = HtmlBuilder()
        .appendRaw(GitBundle.message("warning.message.commit.with.detached.head",
                                     HtmlChunk.text(root.presentableUrl).bold().toString()))
        .br()
        .appendLink(DETACHED_HEAD_HELP_LINK, GitBundle.message("link.label.commit.with.detached.head.read.more"))

      val commit = MessageDialogBuilder.okCancel(title, message.wrapWithHtmlBody().toString())
        .yesText(commitInfo.commitActionText)
        .icon(Messages.getWarningIcon())
        .doNotAsk(detachedHeadDoNotAsk(project))
        .ask(project)

      if (commit) {
        return ReturnResult.COMMIT
      }
      else {
        return ReturnResult.CLOSE_WINDOW
      }
    }

    override val showDetailsLink: String
      get() = GitBundle.message("commit.check.warning.title.commit.with.detached.head.details")

    override val showDetailsAction: String
      get() = GitBundle.message("commit.check.warning.title.commit.with.detached.head.details")

    override fun showDetails(project: Project) {
      BrowserUtil.browse(DETACHED_HEAD_HELP_LINK)
    }
  }

  /**
   * Scans the Git roots, selected for commit, for the root which is on a detached HEAD.
   * Returns null if all repositories are on the branch.
   * There might be several detached repositories - in that case, only one is returned.
   * This is because the situation is very rare, while it requires a lot of additional effort of making a well-formed message.
   */
  private fun getDetachedRoot(commitInfo: CommitInfo): DetachedRoot? {
    val repositoryManager = GitUtil.getRepositoryManager(project)
    for (root in getSelectedRoots(commitInfo)) {
      val repository = repositoryManager.getRepositoryForRootQuick(root) ?: continue
      if (!repository.isOnBranch && !GitRebaseUtils.isInteractiveRebaseInProgress(repository)) {
        return DetachedRoot(root, repository.isRebaseInProgress)
      }
    }
    return null
  }

  private class DetachedRoot(val root: VirtualFile, val isDuringRebase: Boolean)
}

private abstract class GitCheckinHandler(val project: Project) : CheckinHandler(), CommitCheck, DumbAware {
  final override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    if (!commitInfo.isVcsCommit) return null
    return runGitCheck(commitInfo)
  }

  abstract suspend fun runGitCheck(commitInfo: CommitInfo): CommitProblem?

  protected fun getSelectedRoots(commitInfo: CommitInfo): Collection<VirtualFile> {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val git = GitVcs.getInstance(project)
    val result = mutableSetOf<VirtualFile>()
    for (path in ChangesUtil.getPaths(commitInfo.committedChanges)) {
      val vcsRoot = vcsManager.getVcsRootObjectFor(path)
      if (vcsRoot != null) {
        val root = vcsRoot.path
        if (git == vcsRoot.vcs) {
          result.add(root)
        }
      }
    }
    return result
  }
}
