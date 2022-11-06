// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.CommonBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vfs.VirtualFile
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
import org.jetbrains.annotations.Nls
import java.util.*

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

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val git = Git.getInstance()

    val files = commitInfo.committedVirtualFiles // Deleted files aren't included. But for them, we don't care about CRLFs.
    val shouldWarn = withContext(Dispatchers.Default) {
      coroutineContext.progressSink?.update(GitBundle.message("progress.checking.line.separator.issues"))
      runUnderIndicator {
        GitCrlfProblemsDetector.detect(project, git, files).shouldWarn()
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
        setCoreAutoCrlfAttribute(project, anyRoot)
      }
    }
    else if (dontWarnAgain) {
      val settings = GitVcsSettings.getInstance(project)
      settings.setWarnAboutCrlf(false)
    }
    return null
  }

  private fun setCoreAutoCrlfAttribute(project: Project, anyRoot: VirtualFile) {
    runModalTask(GitBundle.message("progress.setting.config.value"), project, true) {
      try {
        GitConfigUtil.setValue(project, anyRoot, GitConfigUtil.CORE_AUTOCRLF, GitCrlfUtil.RECOMMENDED_VALUE, "--global")
      }
      catch (e: VcsException) {
        // it is not critical: the user just will get the dialog again next time
        logger<GitCRLFCheckinHandler>().warn("Couldn't globally set core.autocrlf in $anyRoot", e)
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
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override fun isEnabled(): Boolean = true

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
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

    GitVcsSettings.getInstance(project).setUserNameGlobally(dialog.isGlobal)
    if (setUserNameUnderProgress(project, notDefined, dialog)) {
      return null
    }

    return GitUserNameCommitProblem(closeWindow = false)
  }

  private suspend fun getDefinedUserNames(project: Project,
                                          roots: Collection<VirtualFile>,
                                          stopWhenFoundFirst: Boolean): MutableMap<VirtualFile, Couple<String?>> {
    return withContext(Dispatchers.Default) {
      runUnderIndicator {
        val defined = HashMap<VirtualFile, Couple<String?>>()
        for (root in roots) {
          try {
            val nameAndEmail = getUserNameAndEmailFromGitConfig(project, root)
            val name = nameAndEmail.getFirst()
            val email = nameAndEmail.getSecond()
            if (name != null && email != null) {
              defined[root] = nameAndEmail
              if (stopWhenFoundFirst) {
                break
              }
            }
          }
          catch (e: VcsException) {
            logger<GitUserNameCheckinHandler>().error("Couldn't get user.name and user.email for root $root", e)
            // doing nothing - let commit with possibly empty user.name/email
          }
        }
        defined
      }
    }
  }

  private fun setUserNameUnderProgress(project: Project,
                                       notDefined: Collection<VirtualFile>,
                                       dialog: GitUserNameNotDefinedDialog): Boolean {
    val error = Ref.create<@Nls String?>()
    runModalTask(GitBundle.message("progress.setting.user.name.email"), project, true) {
      try {
        if (dialog.isGlobal) {
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
      catch (e: VcsException) {
        logger<GitUserNameCheckinHandler>().error("Couldn't set user.name and user.email", e)
        error.set(GitBundle.message("error.cant.set.user.name.email"))
      }
    }
    if (error.isNull) {
      return true
    }
    else {
      Messages.showErrorDialog(project, error.get(), CommonBundle.getErrorTitle())
      return false
    }
  }

  @Throws(VcsException::class)
  private fun getUserNameAndEmailFromGitConfig(project: Project,
                                               root: VirtualFile): Couple<String?> {
    val name = GitConfigUtil.getValue(project, root, GitConfigUtil.USER_NAME)
    val email = GitConfigUtil.getValue(project, root, GitConfigUtil.USER_EMAIL)
    return Couple.of(name, email)
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

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val detachedRoot = getDetachedRoot(commitInfo)
    if (detachedRoot == null) return null

    val rootPath = HtmlChunk.text(detachedRoot.root.presentableUrl).bold()
    val title: @NlsContexts.DialogTitle String
    val message: @NlsContexts.DialogMessage HtmlBuilder = HtmlBuilder()
    if (detachedRoot.isDuringRebase) {
      title = GitBundle.message("warning.title.commit.with.unfinished.rebase")
      message
        .appendRaw(GitBundle.message("warning.message.commit.with.unfinished.rebase", rootPath.toString()))
        .br()
        .appendLink("https://git-scm.com/docs/git-rebase",
                    GitBundle.message("link.label.commit.with.unfinished.rebase.read.more"))
    }
    else {
      title = GitBundle.message("warning.title.commit.with.detached.head")
      message
        .appendRaw(GitBundle.message("warning.message.commit.with.detached.head", rootPath.toString()))
        .br()
        .appendLink("https://git-scm.com/docs/git-checkout#_detached_head",
                    GitBundle.message("link.label.commit.with.detached.head.read.more"))
    }

    val dontAskAgain: DoNotAskOption = object : DoNotAskOption.Adapter() {
      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        GitVcsSettings.getInstance(project).setWarnAboutDetachedHead(!isSelected)
      }

      override fun getDoNotShowMessage(): String {
        return GitBundle.message("checkbox.dont.warn.again")
      }
    }
    val commit = MessageDialogBuilder.okCancel(title, message.wrapWithHtmlBody().toString())
      .yesText(commitInfo.commitActionText)
      .icon(Messages.getWarningIcon())
      .doNotAsk(dontAskAgain)
      .ask(project)
    if (commit) return null

    return GitDetachedRootCommitProblem()
  }

  private class GitDetachedRootCommitProblem : CommitProblem {
    override val text: String
      get() = GitBundle.message("commit.check.warning.title.commit.with.detached.head")

    override fun showModalSolution(project: Project, commitInfo: CommitInfo): ReturnResult {
      return ReturnResult.CLOSE_WINDOW // dialog was already shown
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

private abstract class GitCheckinHandler(val project: Project) : CheckinHandler(), CommitCheck {
  override fun acceptExecutor(executor: CommitExecutor?): Boolean {
    return (executor == null || executor is GitCommitAndPushExecutor) &&
           super.acceptExecutor(executor)
  }

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
