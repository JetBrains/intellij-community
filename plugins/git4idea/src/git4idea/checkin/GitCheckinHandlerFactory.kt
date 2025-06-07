// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.vcs.log.VcsUser
import com.intellij.vcsUtil.VcsUtil
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
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.PropertyKey

abstract class GitCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey())

private class GitUserNameCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitUserNameCheckinHandler(panel.project)
  }
}

class GitCRLFCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitCRLFCheckinHandler(panel.project)
  }
}

class GitLargeFileCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitLargeFileCheckinHandler(panel.project)
  }
}

private class GitDetachedRootCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitDetachedRootCheckinHandler(panel.project)
  }
}

private class GitFileNameCheckinHandlerFactory : GitCheckinHandlerFactory() {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return GitFileNameCheckinHandler(panel.project)
  }
}

private class GitCRLFCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override fun isEnabled(): Boolean {
    return GitVcsSettings.getInstance(project).warnAboutCrlf()
  }

  override suspend fun runGitCheck(commitInfo: CommitInfo, committedChanges: List<Change>): CommitProblem? {
    return reportSequentialProgress { reporter ->
      runGitCheck(reporter, committedChanges)
    }
  }

  private suspend fun runGitCheck(reporter: SequentialProgressReporter, committedChanges: List<Change>): CommitProblem? {
    // Deleted files aren't included. But for them, we don't care about CRLFs.
    val files = committedChanges.mapNotNull { it.virtualFile }
    val shouldWarn = reporter.nextStep(endFraction = 100, GitBundle.message("progress.checking.line.separator.issues")) {
      coroutineToIndicator {
        val git = Git.getInstance()
        GitCrlfProblemsDetector.detect(project, git, files).shouldWarn()
      }
    }
    if (!shouldWarn) return null

    val (decision, dontWarnAgain) = withContext(Dispatchers.EDT) {
      val dialog = GitCrlfDialog(project)
      dialog.show()
      val decision = dialog.exitCode
      val dontWarnAgain = dialog.dontWarnAgain()
      (decision to dontWarnAgain)
    }

    if (decision == GitCrlfDialog.CANCEL) {
      return GitCRLFCommitProblem()
    }

    if (decision == GitCrlfDialog.SET) {
      val anyRoot = files
        .mapNotNull { ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(it) }
        .filter { it.vcs is GitVcs }
        .map { it.path }
        .firstOrNull()
      if (anyRoot != null) {
        reporter.indeterminateStep(GitBundle.message("progress.setting.config.value")) {
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

  private class GitCRLFCommitProblem : CommitProblem {
    override val text: String get() = GitBundle.message("text.crlf.fix.notification.description.warning")

    override fun showModalSolution(project: Project, commitInfo: CommitInfo): ReturnResult {
      return ReturnResult.CANCEL // dialog was already shown
    }
  }
}

private class GitLargeFileCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override fun isEnabled(): Boolean = GitVcsSettings.getInstance(project).warnAboutLargeFiles()

  override suspend fun runGitCheck(commitInfo: CommitInfo, committedChanges: List<Change>): CommitProblem? {
    val maxFileSize = GitVcsSettings.getInstance(project).warnAboutLargeFilesLimitMb * 1024 * 1024
    if (maxFileSize <= 0) return null

    val files = committedChanges.mapNotNull { it.virtualFile }
    val largeFiles = files.filter { it.length > maxFileSize }
    if (largeFiles.isEmpty()) return null

    val git = GitVcs.getInstance(project)
    val vcsManager = ProjectLevelVcsManager.getInstance(project)

    val filesByRoot = largeFiles.groupBy { vcsManager.getVcsRootObjectFor(it) }
    val affectedRoots = filesByRoot.keys.filterNotNull()
      .distinct()
      .filter { root -> root.vcs == git }
      .filter { root -> GitConfigUtil.getValue(project, root.path, "lfs.repositoryformatversion") == null }
    if (affectedRoots.isEmpty()) return null

    val affectedFiles = affectedRoots.flatMap { root -> filesByRoot[root].orEmpty() }
    return GitLargeFileCommitProblem(affectedFiles.size, affectedFiles.sumOf { it.length })
  }

  private class GitLargeFileCommitProblem(val fileCount: Int, val totalSizeBytes: Long) : CommitProblem {
    override val text: String
      get() = GitBundle.message("commit.check.warning.title.large.file", fileCount, totalSizeBytes / 1024 / 1024)
  }
}

private class GitUserNameCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.LATE

  override fun isEnabled(): Boolean = true

  override suspend fun runGitCheck(commitInfo: CommitInfo, committedChanges: List<Change>): CommitProblem? {
    return reportSequentialProgress { reporter ->
      runGitCheck(reporter, commitInfo, committedChanges)
    }
  }

  private suspend fun runGitCheck(reporter: SequentialProgressReporter, commitInfo: CommitInfo, committedChanges: List<Change>): CommitProblem? {
    if (commitInfo.commitContext.commitAuthor != null) return null

    val affectedRoots = getSelectedRoots(project, committedChanges)
    val defined = getDefinedUserNames(project, affectedRoots, false)

    val vcs = GitVcs.getInstance(project)
    val allRoots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs).toMutableList()
    val notDefined = affectedRoots.toMutableList()
    notDefined.removeAll(defined.keys)

    if (notDefined.isEmpty()) return null

    // try to find a root with defined user name among other roots - to propose this user name in the dialog
    if (defined.isEmpty() && allRoots.size > affectedRoots.size) {
      allRoots.removeAll(affectedRoots)
      defined.putAll(getDefinedUserNames(project, allRoots, true))
    }


    val data = withContext(Dispatchers.EDT) {
      val dialog = GitUserNameNotDefinedDialog(project, notDefined, affectedRoots, defined, commitInfo.commitActionText)
      val setUserNameEmail = dialog.showAndGet()
      UserNameDialogData(setUserNameEmail, dialog.userName, dialog.userEmail, dialog.isSetGlobalConfig)
    }
    if (!data.setUserNameEmail) return GitUserNameCommitProblem(closeWindow = true)

    val success = reporter.indeterminateStep(GitBundle.message("progress.setting.user.name.email")) {
      setUserNameUnderProgress(project, notDefined, data)
    }
    if (success) return null

    withContext(Dispatchers.EDT) {
      Messages.showErrorDialog(project, GitBundle.message("error.cant.set.user.name.email"), CommonBundle.getErrorTitle())
    }
    return GitUserNameCommitProblem(closeWindow = false)
  }

  private suspend fun getDefinedUserNames(project: Project,
                                          roots: Collection<VirtualFile>,
                                          stopWhenFoundFirst: Boolean): MutableMap<VirtualFile, VcsUser> {
    return withContext(Dispatchers.Default) {
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

  private suspend fun setUserNameUnderProgress(project: Project,
                                               notDefined: Collection<VirtualFile>,
                                               data: UserNameDialogData): Boolean {
    try {
      withContext(Dispatchers.IO) {
        coroutineToIndicator {
          if (data.isSetGlobalConfig) {
            GitConfigUtil.setValue(project, notDefined.iterator().next(), GitConfigUtil.USER_NAME, data.userName, "--global")
            GitConfigUtil.setValue(project, notDefined.iterator().next(), GitConfigUtil.USER_EMAIL, data.userEmail, "--global")
          }
          else {
            for (root in notDefined) {
              GitConfigUtil.setValue(project, root, GitConfigUtil.USER_NAME, data.userName)
              GitConfigUtil.setValue(project, root, GitConfigUtil.USER_EMAIL, data.userEmail)
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

  private class UserNameDialogData(val setUserNameEmail: Boolean,
                                   val userName: String,
                                   val userEmail: String,
                                   val isSetGlobalConfig: Boolean)
}

private class GitDetachedRootCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override fun isEnabled(): Boolean {
    return GitVcsSettings.getInstance(project).warnAboutDetachedHead()
  }

  override suspend fun runGitCheck(commitInfo: CommitInfo, committedChanges: List<Change>): CommitProblem? {
    val selectedRoots = getSelectedRoots(project, committedChanges)
    val detachedRoot = getDetachedRoot(selectedRoots)
    if (detachedRoot == null) return null

    if (detachedRoot.isDuringRebase) {
      return GitRebaseCommitProblem(detachedRoot.root)
    }
    else {
      return GitDetachedRootCommitProblem(detachedRoot.root)
    }
  }

  /**
   * Scans the Git roots, selected for commit, for the root which is on a detached HEAD.
   * Returns null if all repositories are on the branch.
   * There might be several detached repositories - in that case, only one is returned.
   * This is because the situation is very rare, while it requires a lot of additional effort of making a well-formed message.
   */
  private fun getDetachedRoot(roots: List<VirtualFile>): DetachedRoot? {
    val repositoryManager = GitUtil.getRepositoryManager(project)
    for (root in roots) {
      val repository = repositoryManager.getRepositoryForRootQuick(root) ?: continue
      if (!repository.isOnBranch && !GitRebaseUtils.isInteractiveRebaseInProgress(repository)) {
        return DetachedRoot(root, repository.isRebaseInProgress)
      }
    }
    return null
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

  private class DetachedRoot(val root: VirtualFile, val isDuringRebase: Boolean)
}

private class GitFileNameCheckinHandler(project: Project) : GitCheckinHandler(project) {
  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.EARLY

  override fun isEnabled(): Boolean = !SystemInfo.isWindows && GitVcsSettings.getInstance(project).warnAboutBadFileNames()

  override suspend fun runGitCheck(commitInfo: CommitInfo, committedChanges: List<Change>): CommitProblem? {
    for (change in committedChanges) {
      val beforePath = change.beforeRevision?.file
      val afterPath = change.afterRevision?.file
      if (afterPath != null && beforePath != afterPath) {
        val problem = checkFileName(afterPath)
        if (problem != null) return problem
      }
    }
    return null
  }

  private fun checkFileName(filePath: FilePath): GitFileNameCommitProblem? {
    val repo = GitRepositoryManager.getInstance(project).getRepositoryForFile(filePath) ?: return null

    val rootPath = VcsUtil.getFilePath(repo.root)

    var parentPath: FilePath? = filePath
    while (parentPath != null && parentPath != rootPath) {
      val fileName = parentPath.name
      val fileNameWithoutExtension = FileUtil.getNameWithoutExtension(fileName)
      if (fileNameWithoutExtension.length in 3..4 &&
          WINDOWS_RESERVED_NAMES.any { it.equals(fileNameWithoutExtension, ignoreCase = true) }) {
        return GitFileNameCommitProblem(fileName, BadFileNameType.RESERVED)
      }

      if (fileName.any { char -> char.code <= 31 || WINDOWS_INVALID_CHARS.contains(char) }) {
        return GitFileNameCommitProblem(fileName, BadFileNameType.INVALID_CHAR)
      }

      parentPath = parentPath.parentPath
    }
    return null
  }

  enum class BadFileNameType(val msgKey: @PropertyKey(resourceBundle = GitBundle.BUNDLE) String) {
    RESERVED("warning.message.commit.with.bad.windows.file.name.reserved"),
    INVALID_CHAR("warning.message.commit.with.bad.windows.file.name.bad.character")
  }

  companion object {
    private val WINDOWS_INVALID_CHARS = "<>:\"\\|?*"
    private val WINDOWS_RESERVED_NAMES: Set<String> = setOf(
      "CON", "PRN", "AUX", "NUL",
      "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
      "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9")
  }

  private class GitFileNameCommitProblem(val fileName: @NlsSafe String, val type: BadFileNameType) : CommitProblem {
    override val text: String get() = GitBundle.message(type.msgKey, fileName)

    override fun showModalSolution(project: Project, commitInfo: CommitInfo): ReturnResult {
      val title = GitBundle.message("warning.title.commit.with.bad.windows.file.name")
      val message = HtmlBuilder().append(text)

      val commit = MessageDialogBuilder.okCancel(title, message.wrapWithHtmlBody().toString())
        .yesText(commitInfo.commitActionText)
        .icon(Messages.getWarningIcon())
        .ask(project)

      if (commit) {
        return ReturnResult.COMMIT
      }
      else {
        return ReturnResult.CLOSE_WINDOW
      }
    }
  }
}

private abstract class GitCheckinHandler(val project: Project) : CheckinHandler(), CommitCheck, DumbAware {
  final override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    if (!commitInfo.isVcsCommit) return null
    val committedChanges = commitInfo.committedChanges
    return withContext(Dispatchers.IO) {
      runGitCheck(commitInfo, committedChanges)
    }
  }

  abstract suspend fun runGitCheck(commitInfo: CommitInfo, committedChanges: List<Change>): CommitProblem?
}

private fun getSelectedRoots(project: Project, changes: List<Change>): List<VirtualFile> {
  val vcsManager = ProjectLevelVcsManager.getInstance(project)
  val git = GitVcs.getInstance(project)
  val result = mutableSetOf<VirtualFile>()
  for (path in ChangesUtil.getPaths(changes)) {
    val vcsRoot = vcsManager.getVcsRootObjectFor(path)
    if (vcsRoot != null) {
      val root = vcsRoot.path
      if (git == vcsRoot.vcs) {
        result.add(root)
      }
    }
  }
  return result.toList()
}
