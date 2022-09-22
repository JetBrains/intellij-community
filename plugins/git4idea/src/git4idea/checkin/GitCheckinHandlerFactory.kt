// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.CommonBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PairConsumer
import com.intellij.util.ui.UIUtil
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
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Prohibits committing with an empty messages, warns if committing into detached HEAD, checks if user name and correct CRLF attributes
 * are set.
 */
open class GitCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey()) {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitCheckinHandler(panel)
  }
}

private class GitCheckinHandler(private val myPanel: CheckinProjectPanel) : CheckinHandler() {
  private val myProject: Project

  init {
    myProject = myPanel.project
  }

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    if (commitOrCommitAndPush(executor)) {
      var result = checkUserName()
      if (result != ReturnResult.COMMIT) {
        return result
      }
      result = warnAboutCrlfIfNeeded()
      return if (result != ReturnResult.COMMIT) {
        result
      }
      else warnAboutDetachedHeadIfNeeded()
    }
    return ReturnResult.COMMIT
  }

  private fun warnAboutCrlfIfNeeded(): ReturnResult {
    val settings = GitVcsSettings.getInstance(myProject)
    if (!settings.warnAboutCrlf()) {
      return ReturnResult.COMMIT
    }

    val git = Git.getInstance()

    val files = myPanel.virtualFiles // deleted files aren't included, but for them we don't care about CRLFs.
    val crlfHelper = AtomicReference<GitCrlfProblemsDetector?>()
    runModalTask(GitBundle.message("progress.checking.line.separator.issues"), myProject, true) {
      crlfHelper.set(GitCrlfProblemsDetector.detect(myProject, git, files))
    }

    if (crlfHelper.get() == null) { // detection cancelled
      return ReturnResult.CANCEL
    }

    if (crlfHelper.get()!!.shouldWarn()) {
      val codeAndDontWarn = UIUtil.invokeAndWaitIfNeeded<Pair<Int, Boolean>> {
        val dialog = GitCrlfDialog(myProject)
        dialog.show()
        Pair.create(dialog.exitCode, dialog.dontWarnAgain())
      }
      val decision = codeAndDontWarn.first
      val dontWarnAgain = codeAndDontWarn.second

      if (decision == GitCrlfDialog.CANCEL) {
        return ReturnResult.CANCEL
      }
      else {
        if (decision == GitCrlfDialog.SET) {
          val anyRoot = myPanel.roots.iterator().next() // config will be set globally => any root will do.
          setCoreAutoCrlfAttribute(anyRoot)
        }
        else {
          if (dontWarnAgain) {
            settings.setWarnAboutCrlf(false)
          }
        }
        return ReturnResult.COMMIT
      }
    }
    return ReturnResult.COMMIT
  }

  private fun setCoreAutoCrlfAttribute(aRoot: VirtualFile) {
    runModalTask(GitBundle.message("progress.setting.config.value"), myProject, true) {
      try {
        GitConfigUtil.setValue(myProject, aRoot, GitConfigUtil.CORE_AUTOCRLF, GitCrlfUtil.RECOMMENDED_VALUE, "--global")
      }
      catch (e: VcsException) {
        // it is not critical: the user just will get the dialog again next time
        LOG.warn("Couldn't globally set core.autocrlf in $aRoot", e)
      }
    }
  }

  private fun checkUserName(): ReturnResult {
    val project = myPanel.project
    val vcs = GitVcs.getInstance(project)

    val affectedRoots = getSelectedRoots()
    val defined = getDefinedUserNames(project, affectedRoots, false)

    val allRoots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs).toMutableList()
    val notDefined = affectedRoots.toMutableList()
    notDefined.removeAll(defined.keys)

    if (notDefined.isEmpty()) {
      return ReturnResult.COMMIT
    }

    // try to find a root with defined user name among other roots - to propose this user name in the dialog
    if (defined.isEmpty() && allRoots.size > affectedRoots.size) {
      allRoots.removeAll(affectedRoots)
      defined.putAll(getDefinedUserNames(project, allRoots, true))
    }

    val dialog = GitUserNameNotDefinedDialog(project, notDefined, affectedRoots, defined)
    if (dialog.showAndGet()) {
      GitVcsSettings.getInstance(project).setUserNameGlobally(dialog.isGlobal)
      return if (setUserNameUnderProgress(project, notDefined, dialog)) ReturnResult.COMMIT else ReturnResult.CANCEL
    }
    return ReturnResult.CLOSE_WINDOW
  }

  private fun getDefinedUserNames(project: Project,
                                  roots: Collection<VirtualFile>,
                                  stopWhenFoundFirst: Boolean): MutableMap<VirtualFile, Couple<String?>> {
    val defined = HashMap<VirtualFile, Couple<String?>>()
    runModalTask(GitBundle.message("progress.checking.user.name.email"), project, true) {
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
          LOG.error("Couldn't get user.name and user.email for root $root", e)
          // doing nothing - let commit with possibly empty user.name/email
        }
      }
    }
    return defined
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
        LOG.error("Couldn't set user.name and user.email", e)
        error.set(GitBundle.message("error.cant.set.user.name.email"))
      }
    }
    if (error.isNull) {
      return true
    }
    else {
      Messages.showErrorDialog(myPanel.component, error.get())
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

  private fun warnAboutDetachedHeadIfNeeded(): ReturnResult {
    // Warning: commit on a detached HEAD
    val detachedRoot = getDetachedRoot()
    if (detachedRoot == null || !GitVcsSettings.getInstance(myProject).warnAboutDetachedHead()) {
      return ReturnResult.COMMIT
    }

    val rootPath: HtmlChunk = HtmlChunk.text(detachedRoot.myRoot.presentableUrl).bold()
    val title: @NlsContexts.DialogTitle String
    val message: @NlsContexts.DialogMessage HtmlBuilder = HtmlBuilder()
    if (detachedRoot.myRebase) {
      title = GitBundle.message("warning.title.commit.with.unfinished.rebase")
      message
        .appendRaw(GitBundle.message("warning.message.commit.with.unfinished.rebase", rootPath.toString()))
        .br()
        .appendLink("https://www.kernel.org/pub/software/scm/git/docs/git-rebase.html",
                    GitBundle.message("link.label.commit.with.unfinished.rebase.read.more"))
    }
    else {
      title = GitBundle.message("warning.title.commit.with.detached.head")
      message
        .appendRaw(GitBundle.message("warning.message.commit.with.detached.head", rootPath.toString()))
        .br()
        .appendLink("http://gitolite.com/detached-head.html",
                    GitBundle.message("link.label.commit.with.detached.head.read.more"))
    }

    val dontAskAgain: DialogWrapper.DoNotAskOption = object : DialogWrapper.DoNotAskOption.Adapter() {
      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        GitVcsSettings.getInstance(myProject).setWarnAboutDetachedHead(!isSelected)
      }

      override fun getDoNotShowMessage(): String {
        return GitBundle.message("checkbox.dont.warn.again")
      }
    }
    val choice = Messages.showOkCancelDialog(myProject, message.wrapWithHtmlBody().toString(), title,
                                             GitBundle.message("commit.action.name"), CommonBundle.getCancelButtonText(),
                                             Messages.getWarningIcon(), dontAskAgain)
    if (choice == Messages.OK) {
      return ReturnResult.COMMIT
    }
    else {
      return ReturnResult.CLOSE_WINDOW
    }
  }

  private fun commitOrCommitAndPush(executor: CommitExecutor?): Boolean {
    return executor == null || executor is GitCommitAndPushExecutor
  }

  /**
   * Scans the Git roots, selected for commit, for the root which is on a detached HEAD.
   * Returns null, if all repositories are on the branch.
   * There might be several detached repositories, - in that case only one is returned.
   * This is because the situation is very rare, while it requires a lot of additional effort of making a well-formed message.
   */
  private fun getDetachedRoot(): DetachedRoot? {
    val repositoryManager = GitUtil.getRepositoryManager(myPanel.project)
    for (root in getSelectedRoots()) {
      val repository = repositoryManager.getRepositoryForRootQuick(root) ?: continue
      if (!repository.isOnBranch && !GitRebaseUtils.isInteractiveRebaseInProgress(repository)) {
        return DetachedRoot(root, repository.isRebaseInProgress)
      }
    }
    return null
  }

  private fun getSelectedRoots(): Collection<VirtualFile> {
    val vcsManager = ProjectLevelVcsManager.getInstance(myProject)
    val git = GitVcs.getInstance(myProject)
    val result = mutableSetOf<VirtualFile>()
    for (path in ChangesUtil.getPaths(myPanel.selectedChanges)) {
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

  private class DetachedRoot(val myRoot: VirtualFile, // rebase in progress, or just detached due to a checkout of a commit.
                             val myRebase: Boolean)

  companion object {
    private val LOG = Logger.getInstance(GitCheckinHandlerFactory::class.java)
  }
}
