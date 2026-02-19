// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.google.common.collect.HashMultiset
import com.intellij.diff.util.Side
import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.checkin.PostCommitChangeConverter
import com.intellij.openapi.vcs.ex.PartialCommitHelper
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.util.ArrayUtil
import com.intellij.util.ThrowableConsumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.system.OS
import com.intellij.vcs.commit.AmendCommitAware
import com.intellij.vcs.commit.CommitToAmend
import com.intellij.vcs.commit.ToggleAmendCommitOption.Companion.isAmendCommitOptionSupported
import com.intellij.vcs.commit.commitToAmend
import com.intellij.vcs.commit.commitWithoutChangesRoots
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.checkin.GitCheckinExplicitMovementProvider.Movement
import git4idea.commit.GitMergeCommitMessageReader
import git4idea.config.GitConfigUtil
import git4idea.config.GitEelExecutableDetectionHelper
import git4idea.i18n.GitBundle
import git4idea.index.GitIndexUtil
import git4idea.index.GitIndexUtil.StagedFile
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRepository
import git4idea.repo.isSubmodule
import git4idea.util.GitFileUtils
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.CancellablePromise
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class GitCheckinEnvironment(private val myProject: Project) : CheckinEnvironment, AmendCommitAware {
  private var myNextCommitAuthor: VcsUser? = null // The author for the next commit
  private var myNextCommitToAmend: CommitToAmend = CommitToAmend.None
  private var myNextCommitAuthorDate: Date? = null
  private var myNextCommitSignOff = false
  private var myNextCommitSkipHook = false
  private var myNextCleanupCommitMessage = false

  override fun isRefreshAfterCommitNeeded(): Boolean {
    return true
  }

  override fun createCommitOptions(
    commitPanel: CheckinProjectPanel,
    commitContext: CommitContext,
  ): RefreshableOnComponent {
    return GitCheckinOptions(commitPanel, commitContext, isAmendCommitOptionSupported(commitPanel, this))
  }

  override fun getDefaultMessageFor(filesToCheckin: Array<FilePath>): String? {
    val manager = GitUtil.getRepositoryManager(myProject)
    val repositories = filesToCheckin.mapNotNullTo(HashSet()) { file -> manager.getRepositoryForFileQuick(file) }
    val singleRepo = repositories.singleOrNull() ?: return null
    return GitMergeCommitMessageReader.getInstance(myProject).read(singleRepo)
  }

  override fun getHelpId(): String? {
    return null
  }

  override fun getCheckinOperationName(): String {
    // To fix Mac commit mnemonic issue (IJPL-54603) while keeping same behavior for other users
    return when (OS.CURRENT) {
      OS.macOS -> GitBundle.message("commit.action.name.mac")
      OS.Windows,
      OS.Linux,
      OS.FreeBSD,
      OS.Other -> GitBundle.message("commit.action.name")
    }
  }

  override fun isAmendCommitSupported(): Boolean {
    return amendService.isAmendCommitSupported()
  }

  override fun isAmendSpecificCommitSupported(): Boolean {
    return amendService.isAmendSpecificCommitSupported()
  }

  override suspend fun getAmendSpecificCommitTargets(root: VirtualFile, limit: Int): List<CommitToAmend.Specific> {
    return amendService.getAmendSpecificCommitTargets(root, limit)
  }

  @Throws(VcsException::class)
  override fun getLastCommitMessage(root: VirtualFile): String {
    return amendService.getLastCommitMessage(root)
  }

  override fun getAmendCommitDetails(root: VirtualFile, commitToAmend: CommitToAmend): CancellablePromise<EditedCommitDetails> {
    return amendService.getAmendCommitDetails(root, commitToAmend)
  }

  private val amendService: GitAmendCommitService get() = myProject.getService(GitAmendCommitService::class.java)

  private fun updateState(commitContext: CommitContext) {
    myNextCommitToAmend = commitContext.commitToAmend
    myNextCommitSkipHook = commitContext.isSkipHooks
    myNextCommitAuthor = commitContext.commitAuthor
    myNextCommitAuthorDate = commitContext.commitAuthorDate
    myNextCommitSignOff = commitContext.isSignOffCommit
    myNextCleanupCommitMessage = GitCommitTemplateTracker.getInstance(myProject).exists()
  }

  override fun commit(
    changes: List<Change>,
    commitMessage: @NonNls String,
    commitContext: CommitContext,
    feedback: MutableSet<in String>,
  ): List<VcsException> {
    updateState(commitContext)

    val exceptions = mutableListOf<VcsException>()
    val sortedChanges = sortChangesByGitRoot(myProject, changes, exceptions)
    val commitWithoutChangesRoots = commitContext.commitWithoutChangesRoots
    LOG.assertTrue(!sortedChanges.isEmpty() || !commitWithoutChangesRoots.isEmpty(),
                   "Trying to commit an empty list of changes: $changes")

    val commitOptions = createCommitOptions()

    val repositories = collectRepositories(sortedChanges.keys, commitWithoutChangesRoots)

    runCommitPossiblyFreezingLog(commitOptions, repositories, sortedChanges, commitContext, commitMessage, exceptions)
    return exceptions
  }

  private fun runCommitPossiblyFreezingLog(
    commitOptions: GitCommitOptions,
    repositories: List<GitRepository>,
    sortedChanges: Map<GitRepository, Collection<Change>>,
    commitContext: CommitContext,
    commitMessage: @NonNls String,
    exceptions: MutableList<VcsException>,
  ) {
    val commitAction = {
      doCommit(repositories, sortedChanges, commitContext, commitMessage, commitOptions, exceptions)
    }

    val needsLogFreeze = commitOptions.commitToAmend is CommitToAmend.Specific
    if (!needsLogFreeze) {
      commitAction()
      return
    }

    val repository = repositories.singleOrNull() ?: error("Freezing log is supported only for single repository commits")
    val logManager = repository.let { VcsProjectLog.getInstance(it.project).logManager }
    if (logManager == null) {
      commitAction()
      return
    }

    runBlockingCancellable {
      logManager.runWithFreezing {
        commitAction()
      }
    }
  }

  private fun doCommit(
    repositories: List<GitRepository>,
    sortedChanges: Map<GitRepository, Collection<Change>>,
    commitContext: CommitContext,
    commitMessage: @NonNls String,
    commitOptions: GitCommitOptions,
    exceptions: MutableList<VcsException>,
  ) {

    for (repository in repositories) {
      val rootChanges: Collection<Change> = sortedChanges.getOrDefault(repository, ContainerUtil.emptyList())
      var toCommit: Collection<CommitChange> = collectChangesToCommit(rootChanges)

      if (commitContext.isCommitRenamesSeparately) {
        val (explicitToCommit, moveExceptions) = commitExplicitRenames(repository, toCommit, commitMessage, commitOptions)
        toCommit = explicitToCommit

        if (!moveExceptions.isEmpty()) {
          exceptions.addAll(moveExceptions)
          continue
        }
      }

      exceptions.addAll(commitRepository(repository, toCommit, commitMessage, commitContext, commitOptions))
    }

    if (exceptions.isEmpty()) {
      if (commitContext.isPushAfterCommit) {
        GitPushAfterCommitDialog.showOrPush(myProject, repositories)
      }
    }
  }

  private fun collectRepositories(
    changesRepositories: Collection<GitRepository>,
    noChangesRoots: Collection<VcsRoot>,
  ): List<GitRepository> {
    val repositoryManager = GitUtil.getRepositoryManager(myProject)
    val vcs = GitVcs.getInstance(myProject)
    val noChangesRepositories =
      GitUtil.getRepositoriesFromRoots(repositoryManager,
                                       noChangesRoots.mapNotNull { if (it.vcs == vcs) it.path else null })

    return repositoryManager.sortByDependency(ContainerUtil.union(changesRepositories, noChangesRepositories))
  }

  private fun createCommitOptions(): GitCommitOptions {
    return GitCommitOptions(myNextCommitToAmend, myNextCommitSignOff, myNextCommitSkipHook, myNextCommitAuthor, myNextCommitAuthorDate,
                            myNextCleanupCommitMessage)
  }

  override fun scheduleMissingFileForDeletion(files: List<FilePath>): List<VcsException> {
    val rc = mutableListOf<VcsException>()
    val sortedFiles = try {
      GitUtil.sortFilePathsByGitRoot(myProject, files)
    }
    catch (e: VcsException) {
      rc.add(e)
      return rc
    }

    for ((root, value) in sortedFiles) {
      try {
        GitFileUtils.deletePaths(myProject, root, value)
        markRootDirty(root)
      }
      catch (ex: VcsException) {
        rc.add(ex)
      }
    }
    return rc
  }

  override fun scheduleUnversionedFilesForAddition(files: List<VirtualFile>): List<VcsException> {
    val rc = mutableListOf<VcsException>()
    val sortedFiles = try {
      GitUtil.sortFilesByGitRoot(myProject, files)
    }
    catch (e: VcsException) {
      rc.add(e)
      return rc
    }

    for ((root, value) in sortedFiles) {
      try {
        GitFileUtils.addFiles(myProject, root, value)
        markRootDirty(root)
      }
      catch (ex: VcsException) {
        rc.add(ex)
      }
    }
    return rc
  }

  private fun markRootDirty(root: VirtualFile) {
    // Note that the root is invalidated because changes are detected per-root anyway.
    // Otherwise it is not possible to detect moves.
    VcsDirtyScopeManager.getInstance(myProject).rootDirty(root)
  }

  override fun getPostCommitChangeConverter(): PostCommitChangeConverter {
    return GitPostCommitChangeConverter(myProject)
  }

  companion object {
    private val LOG = Logger.getInstance(GitCheckinEnvironment::class.java)
    private val GIT_COMMIT_MSG_FILE_PREFIX: @NonNls String = "git-commit-msg-" // the file name prefix for commit message file
    private val GIT_COMMIT_MSG_FILE_EXT: @NonNls String = ".txt" // the file extension for commit message file

    @JvmField
    val COMMIT_DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")


    private fun commitRepository(
      repository: GitRepository,
      changes: Collection<CommitChange>,
      message: @NonNls String,
      commitContext: CommitContext,
      commitOptions: GitCommitOptions,
    ): List<VcsException> {
      val exceptions = mutableListOf<VcsException>()
      val project = repository.project

      try {
        // Stage partial changes
        val (partialCommitHelpers, partialChanges) = addPartialChangesToIndex(repository, changes)
        val changedWithIndex = HashSet(partialChanges)

        // Stage case-only renames
        val caseOnlyRenameChanges = addCaseOnlyRenamesToIndex(repository, changes, changedWithIndex, exceptions)
        if (!exceptions.isEmpty()) return exceptions
        changedWithIndex.addAll(caseOnlyRenameChanges)

        exceptions.addAll(commitUsingIndex(project, repository, changes, changedWithIndex,
                                           message, commitOptions))
        if (!exceptions.isEmpty()) return exceptions

        applyPartialChanges(partialCommitHelpers)

        repository.update()
        if (repository.isSubmodule()) {
          VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(repository.root.parent)
        }

        GitPostCommitChangeConverter.markRepositoryCommit(commitContext, repository)
      }
      catch (e: VcsException) {
        exceptions.add(e)
      }
      return exceptions
    }

    @JvmStatic
    fun commitUsingIndex(
      project: Project,
      repository: GitRepository,
      rootChanges: Collection<ChangedPath>,
      changedWithIndex: Set<ChangedPath>,
      message: String,
      commitOptions: GitCommitOptions,
    ): List<VcsException> = stageAndCommit(project, repository, rootChanges, changedWithIndex, commitOptions) { committer ->
      committer.commitStaged(message)
    }

    @Deprecated("Use commitUsingIndex(..., message: String, ...) instead")
    @JvmStatic
    fun commitUsingIndex(
      project: Project,
      repository: GitRepository,
      rootChanges: Collection<ChangedPath>,
      changedWithIndex: Set<ChangedPath>,
      messageFile: File,
      commitOptions: GitCommitOptions,
    ): List<VcsException> = stageAndCommit(project, repository, rootChanges, changedWithIndex, commitOptions) { committer ->
      committer.commitStaged(messageFile)
    }

    private fun stageAndCommit(
      project: Project,
      repository: GitRepository,
      rootChanges: Collection<ChangedPath>,
      changedWithIndex: Set<ChangedPath>,
      commitOptions: GitCommitOptions,
      commitStaged: (GitRepositoryCommitter) -> Unit,
    ): List<VcsException> {
      val exceptions = mutableListOf<VcsException>()
      try {
        val toCommitAdded: Set<FilePath> = rootChanges.mapNotNullTo(HashSet()) { it.afterPath }
        val toCommitRemoved: Set<FilePath> = rootChanges.mapNotNullTo(HashSet()) { it.beforePath }

        // Save and reset what is staged besides our changes
        val stagingAreaManager = GitStagingAreaStateManager.create(repository)
        stagingAreaManager.prepareStagingArea(toCommitAdded, toCommitRemoved)

        stagingAreaManager.use {
          val alreadyHandledPaths = getPaths(changedWithIndex)
          // Stage what else is needed to commit
          val toAdd = HashSet(toCommitAdded)
          toAdd.removeAll(alreadyHandledPaths)

          val toRemove = HashSet(toCommitRemoved)
          toRemove.removeAll(toAdd)
          toRemove.removeAll(alreadyHandledPaths)

          LOG.debug(String.format("Updating index: added: %s, removed: %s", toAdd, toRemove))
          GitFileUtils.stageForCommit(project, repository.root, toAdd, toRemove, exceptions)
          if (!exceptions.isEmpty()) return exceptions

          // Commit the staging area
          LOG.debug("Performing commit...")
          val committer = GitRepositoryCommitter(repository, commitOptions)
          commitStaged(committer)
        }
      }
      catch (e: VcsException) {
        exceptions.add(e)
      }
      return exceptions
    }

    @Throws(VcsException::class)
    private fun addPartialChangesToIndex(
      repository: GitRepository,
      changes: Collection<CommitChange>,
    ): Pair<List<PartialCommitHelper>, List<CommitChange>> {
      val project = repository.project

      if (changes.none { it.changelistIds != null }) {
        return Pair(emptyList(), emptyList())
      }

      val (helpers, partialChanges) = computeAfterLSTManagerUpdate<Pair<List<PartialCommitHelper>, List<CommitChange>>?>(project) {
        val helpers = mutableListOf<PartialCommitHelper>()
        val partialChanges = mutableListOf<CommitChange>()

        for (change in changes) {
          if (change.changelistIds != null && change.virtualFile != null &&
              change.beforePath != null && change.afterPath != null) {
            val tracker = PartialChangesUtil.getPartialTracker(project, change.virtualFile)
            if (tracker != null && tracker.hasPartialChangesToCommit()) {
              if (!tracker.isOperational()) {
                LOG.warn("Tracker is not operational for " + tracker.virtualFile.presentableUrl)
                return@computeAfterLSTManagerUpdate null // commit failure
              }

              helpers.add(tracker.handlePartialCommit(Side.LEFT, change.changelistIds, true))
              partialChanges.add(change)
            }
          }
        }
        Pair(helpers, partialChanges)
      } ?: throw VcsException(GitBundle.message("error.commit.cant.collect.partial.changes"))


      val pathsToDelete = mutableListOf<FilePath>()
      for (change in partialChanges) {
        if (change.isMove) {
          pathsToDelete.add(change.beforePath!!)
        }
      }
      LOG.debug(String.format("Updating index for partial changes: removing: %s", pathsToDelete))
      GitFileUtils.deletePaths(project, repository.root, pathsToDelete, "--ignore-unmatch")


      LOG.debug(String.format("Updating index for partial changes: changes: %s", partialChanges))
      for (i in partialChanges.indices) {
        val change = partialChanges[i]

        val path = change.afterPath!!
        val helper = helpers[i]
        val file = change.virtualFile ?: throw VcsException(DiffBundle.message("cannot.find.file.error", path.presentableUrl))

        val stagedFile = getStagedFile(repository, change)
        val isExecutable = stagedFile != null && stagedFile.isExecutable

        val fileContent = convertDocumentContentToBytesWithBOM(repository, helper.content, file)

        GitIndexUtil.write(repository, path, fileContent, isExecutable)
      }

      return Pair(helpers, partialChanges)
    }

    private fun applyPartialChanges(partialCommitHelpers: List<PartialCommitHelper>) {
      ApplicationManager.getApplication().invokeLater {
        for (helper in partialCommitHelpers) {
          try {
            helper.applyChanges()
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
      }
    }

    private fun convertDocumentContentToBytes(
      repository: GitRepository,
      documentContent: @NonNls String,
      file: VirtualFile,
    ): ByteArray {
      val lineSeparator = FileDocumentManager.getInstance().getLineSeparator(file, repository.project)
      val text = if (lineSeparator == "\n") {
        documentContent
      }
      else {
        StringUtil.convertLineSeparators(documentContent, lineSeparator)
      }

      return LoadTextUtil.charsetForWriting(repository.project, file, text, file.charset).second
    }

    @JvmStatic
    fun convertDocumentContentToBytesWithBOM(
      repository: GitRepository,
      documentContent: @NonNls String,
      file: VirtualFile,
    ): ByteArray {
      var fileContent = convertDocumentContentToBytes(repository, documentContent, file)

      val bom = file.bom
      if (bom != null && !ArrayUtil.startsWith(fileContent, bom)) {
        fileContent = ArrayUtil.mergeArrays(bom, fileContent)
      }

      return fileContent
    }

    @Throws(VcsException::class)
    private fun getStagedFile(repository: GitRepository, change: CommitChange): StagedFile? {
      val bPath = change.beforePath
      if (bPath != null) {
        val file = GitIndexUtil.listStaged(repository, bPath)
        if (file != null) return file
      }

      val aPath = change.afterPath
      if (aPath != null) {
        val file = GitIndexUtil.listStaged(repository, aPath)
        if (file != null) return file
      }
      return null
    }

    private fun <T> computeAfterLSTManagerUpdate(project: Project, computation: Computable<T>): T? {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      val ref = CompletableFuture<T>()
      LineStatusTrackerManager.getInstance(project).invokeAfterUpdate {
        try {
          ref.complete(computation.compute())
        }
        catch (e: Throwable) {
          ref.completeExceptionally(e)
        }
      }
      try {
        return ProgressIndicatorUtils.awaitWithCheckCanceled(ref)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.warn(e)
        return null
      }
    }


    private fun addCaseOnlyRenamesToIndex(
      repository: GitRepository,
      changes: Collection<CommitChange>,
      alreadyProcessed: Set<CommitChange>,
      exceptions: MutableList<in VcsException>,
    ): List<CommitChange> {
      if (SystemInfo.isFileSystemCaseSensitive) return emptyList()

      val caseOnlyRenames = changes.filter { change -> !alreadyProcessed.contains(change) && isCaseOnlyRename(change) }
      if (caseOnlyRenames.isEmpty()) return emptyList()

      LOG.info("Committing case only rename: " + getLogString(repository.root.path, caseOnlyRenames) +
               " in " + DvcsUtil.getShortRepositoryName(repository))

      val pathsToAdd = caseOnlyRenames.map { it.afterPath!! }
      val pathsToDelete = caseOnlyRenames.map { it.beforePath!! }

      LOG.debug(String.format("Updating index for case only changes: added: %s,\n removed: %s", pathsToAdd, pathsToDelete))
      GitFileUtils.stageForCommit(repository.project, repository.root, pathsToAdd, pathsToDelete, exceptions)

      return caseOnlyRenames
    }

    private fun getPaths(changes: Collection<ChangedPath>): List<FilePath> {
      val files = mutableListOf<FilePath>()
      for (change in changes) {
        if (ChangesUtil.equalsCaseSensitive(change.beforePath, change.afterPath)) {
          files.addIfNotNull(change.beforePath)
        }
        else {
          files.addIfNotNull(change.beforePath)
          files.addIfNotNull(change.afterPath)
        }
      }
      return files
    }

    private fun commitExplicitRenames(
      repository: GitRepository,
      changes: Collection<CommitChange>,
      message: @NonNls String,
      commitOptions: GitCommitOptions,
    ): Pair<Collection<CommitChange>, List<VcsException>> {
      val project = repository.project

      val providers = GitCheckinExplicitMovementProvider.EP_NAME.extensionList.filter { it.isEnabled(project) }

      val exceptions = mutableListOf<VcsException>()
      var newMessage = message
      val issueLinks = getIssueLinks(project, newMessage)

      val beforePaths = changes.mapNotNull { it.beforePath }
      val afterPaths = changes.mapNotNull { it.afterPath }

      val movedPaths = HashSet<Movement>()
      for (provider in providers) {
        val providerMovements = provider.collectExplicitMovements(project, beforePaths, afterPaths)
        if (!providerMovements.isEmpty()) {
          newMessage = provider.getCommitMessage(newMessage)
          movedPaths.addAll(providerMovements)
        }
      }

      if (!issueLinks.isBlank()) {
        newMessage += "\n\n" + issueLinks
      }

      try {
        val (movedChanges, newRootChanges) = addExplicitMovementsToIndex(repository, changes, movedPaths)
                                             ?: return Pair(changes, exceptions)

        exceptions.addAll(commitUsingIndex(project, repository, movedChanges, HashSet(movedChanges),
                                           newMessage, commitOptions))

        val committedMovements = movedChanges.map { Couple.of(it.beforePath, it.afterPath) }
        for (provider in providers) {
          provider.afterMovementsCommitted(project, committedMovements)
        }

        return Pair(newRootChanges, exceptions)
      }
      catch (e: VcsException) {
        exceptions.add(e)
        return Pair(changes, exceptions)
      }
    }

    private fun getIssueLinks(project: Project, message: String): String {
      val matches = IssueNavigationConfiguration.getInstance(project).findIssueLinks(message)
      val builder = StringBuilder()
      for (match in matches) {
        val issueId = match.range.substring(message)
        builder.append(issueId).append("\n")
      }
      return builder.toString()
    }

    @Throws(VcsException::class)
    private fun addExplicitMovementsToIndex(
      repository: GitRepository,
      changes: Collection<CommitChange>,
      explicitMoves: Collection<Movement>,
    ): Pair<List<CommitChange>, List<CommitChange>>? {
      val explicitMoves = filterExcludedChanges(explicitMoves, changes)
      if (explicitMoves.isEmpty()) return null
      LOG.info("Committing explicit rename: " + explicitMoves + " in " + DvcsUtil.getShortRepositoryName(repository))

      val movesMap = HashMap<FilePath, Movement>()
      for (move in explicitMoves) {
        movesMap[move.before] = move
        movesMap[move.after] = move
      }


      val nextCommitChanges = mutableListOf<CommitChange>()
      val movedChanges = mutableListOf<CommitChange>()

      val affectedBeforePaths = HashMap<FilePath, CommitChange>()
      val affectedAfterPaths = HashMap<FilePath, CommitChange>()
      for (change in changes) {
        if (!movesMap.containsKey(change.beforePath) &&
            !movesMap.containsKey(change.afterPath)) {
          nextCommitChanges.add(change) // is not affected by explicit move
        }
        else {
          if (change.beforePath != null) affectedBeforePaths[change.beforePath] = change
          if (change.afterPath != null) affectedAfterPaths[change.afterPath] = change
        }
      }


      val pathsToDelete = explicitMoves.map { move -> move.before }
      LOG.debug(String.format("Updating index for explicit movements: removing: %s", pathsToDelete))
      GitFileUtils.deletePaths(repository.project, repository.root, pathsToDelete, "--ignore-unmatch")


      for (move in explicitMoves) {
        val beforeFilePath = move.before
        val afterFilePath = move.after
        val bChange = affectedBeforePaths[beforeFilePath]!!
        val aChange = affectedAfterPaths[afterFilePath]!!

        val bRev = bChange.beforeRevision
        if (bRev == null) {
          LOG.warn(String.format("Unknown before revision: %s, %s", bChange, aChange))
          continue
        }

        val stagedFile = GitIndexUtil.listTree(repository, beforeFilePath, bRev)
        if (stagedFile == null) {
          LOG.warn(String.format("Can't get revision for explicit move: %s -> %s", beforeFilePath, afterFilePath))
          continue
        }

        LOG.debug(String.format("Updating index for explicit movements: adding movement: %s -> %s", beforeFilePath, afterFilePath))
        val hash = HashImpl.build(stagedFile.blobHash)
        val isExecutable = stagedFile.isExecutable
        GitIndexUtil.updateIndex(repository, afterFilePath, hash, isExecutable)

        // We do not use revision numbers after, and it's unclear which numbers should be used. For now, just pass null values.
        nextCommitChanges.add(CommitChange(afterFilePath, afterFilePath,
                                           null, null,
                                           aChange.changelistIds, aChange.virtualFile))
        movedChanges.add(CommitChange(beforeFilePath, afterFilePath,
                                      null, null,
                                      null, null))

        affectedBeforePaths.remove(beforeFilePath)
        affectedAfterPaths.remove(afterFilePath)
      }

      // Commit leftovers as added/deleted files (ex: if git detected files movements in a conflicting way)
      affectedBeforePaths.forEach { (_, change: CommitChange) ->
        nextCommitChanges.add(CommitChange(change.beforePath, null,
                                           change.beforeRevision, null,
                                           change.changelistIds, change.virtualFile))
      }
      affectedAfterPaths.forEach { (_, change: CommitChange) ->
        nextCommitChanges.add(CommitChange(null, change.afterPath,
                                           null, change.afterRevision,
                                           change.changelistIds, change.virtualFile))
      }

      if (movedChanges.isEmpty()) return null
      return Pair(movedChanges, nextCommitChanges)
    }

    private fun filterExcludedChanges(
      explicitMoves: Collection<Movement>,
      changes: Collection<CommitChange>,
    ): List<Movement> {
      val movedPathsMultiSet = HashMultiset.create<FilePath>()
      for (move in explicitMoves) {
        movedPathsMultiSet.add(move.before)
        movedPathsMultiSet.add(move.after)
      }

      val beforePathsMultiSet = HashMultiset.create<FilePath>()
      val afterPathsMultiSet = HashMultiset.create<FilePath>()
      for (change in changes) {
        ContainerUtil.addIfNotNull(beforePathsMultiSet, change.beforePath)
        ContainerUtil.addIfNotNull(afterPathsMultiSet, change.afterPath)
      }
      return explicitMoves.filter { move ->
        movedPathsMultiSet.count(move.before) == 1 && movedPathsMultiSet.count(move.after) == 1 &&
        beforePathsMultiSet.count(move.before) == 1 && afterPathsMultiSet.count(move.after) == 1 &&
        beforePathsMultiSet.count(move.after) == 0 && afterPathsMultiSet.count(move.before) == 0
      }
    }

    private fun collectChangesToCommit(changes: Collection<Change>): List<CommitChange> {
      val result = mutableListOf<CommitChange>()
      val map = MultiMap<VirtualFile, CommitChange>()

      for (change in changes) {
        val commitChange = createCommitChange(change)
        if (commitChange.virtualFile != null) {
          map.putValue(commitChange.virtualFile, commitChange)
        }
        else {
          result.add(commitChange)
        }
      }

      for ((virtualFile, fileCommitChanges) in map.entrySet()) {
        if (fileCommitChanges.size < 2) {
          result.addAll(fileCommitChanges)
          continue
        }

        val hasSpecificChangelists = fileCommitChanges.any { change -> change.changelistIds != null }
        if (!hasSpecificChangelists) {
          result.addAll(fileCommitChanges)
          continue
        }

        val hasNonChangelists = fileCommitChanges.any { change -> change.changelistIds == null }
        val hasDeletions = fileCommitChanges.any { change -> change.afterPath == null }
        val hasAdditions = fileCommitChanges.any { change -> change.beforePath == null }
        if (hasNonChangelists || hasDeletions) {
          LOG.warn(String.format("Ignoring changelists on commit of %s: %s", virtualFile, fileCommitChanges))
          result.addAll(fileCommitChanges.map { change ->
            CommitChange(change.beforePath, change.afterPath,
                         change.beforeRevision, change.afterRevision,
                         null, change.virtualFile)
          })
          continue
        }

        val firstChange = fileCommitChanges.first()
        val beforePath = if (hasAdditions) null else firstChange.beforePath
        val afterPath = firstChange.afterPath!!
        val beforeRevision = firstChange.beforeRevision
        val afterRevision = firstChange.afterRevision
        val combinedChangeListIds = HashSet<String>()
        var hasMismatch = false

        for (change in fileCommitChanges) {
          combinedChangeListIds.addAll(change.changelistIds.orEmpty())

          if (beforePath != change.beforePath ||
              afterPath != change.afterPath) {
            // VcsRevisionNumber mismatch is not that important
            hasMismatch = true
          }
        }
        if (hasMismatch) {
          LOG.error(String.format("Change mismatch on commit of %s: %s", virtualFile, fileCommitChanges))
        }

        result.add(CommitChange(beforePath, afterPath,
                                beforeRevision, afterRevision,
                                ArrayList(combinedChangeListIds), virtualFile))
      }

      return result
    }

    private fun createCommitChange(change: Change): CommitChange {
      val beforePath = ChangesUtil.getBeforePath(change)
      val afterPath = ChangesUtil.getAfterPath(change)

      val bRev = change.beforeRevision
      val aRev = change.afterRevision
      val beforeRevision = bRev?.revisionNumber
      val afterRevision = aRev?.revisionNumber

      val changelistIds: List<String>? = if (change is ChangeListChange) listOf(change.changeListId) else null
      val virtualFile = if (aRev is CurrentContentRevision) aRev.virtualFile else null

      return CommitChange(beforePath, afterPath, beforeRevision, afterRevision, changelistIds, virtualFile)
    }

    /**
     * Create a file that contains the specified message
     *
     * @param root    a git repository root
     * @param message a message to write
     * @return a file reference
     * @throws IOException if file cannot be created
     */
    @JvmStatic
    @Throws(IOException::class)
    fun createCommitMessageFile(project: Project, root: VirtualFile, message: @NonNls String): File {
      // filter comment lines
      val file = if (GitEelExecutableDetectionHelper.canUseEel()) {
        EelPathUtils.createTemporaryFile(project, GIT_COMMIT_MSG_FILE_PREFIX, GIT_COMMIT_MSG_FILE_EXT, true)
      }
      else {
        FileUtil.createTempFile(GIT_COMMIT_MSG_FILE_PREFIX, GIT_COMMIT_MSG_FILE_EXT).also {
          @Suppress("SSBasedInspection")
          it.deleteOnExit()
        }.toPath()
      }

      val encoding = GitConfigUtil.getCommitEncodingCharsetCached(project, root)

      OutputStreamWriter(Files.newOutputStream(file), encoding).use { out ->
        out.write(message)
      }
      return file.toFile()
    }

    @Throws(VcsException::class)
    @JvmStatic
    fun runWithMessageFile(
      project: Project, root: VirtualFile, message: @NonNls String,
      task: ThrowableConsumer<in File, out VcsException>,
    ) {
      val messageFile = try {
        createCommitMessageFile(project, root, message)
      }
      catch (ex: IOException) {
        throw VcsException(GitBundle.message("error.commit.cant.create.message.file"), ex)
      }

      try {
        task.consume(messageFile)
      }
      finally {
        if (!messageFile.delete()) {
          LOG.warn("Failed to remove temporary file: $messageFile")
        }
      }
    }

    private fun sortChangesByGitRoot(
      project: Project,
      changes: List<Change>,
      exceptions: MutableList<in VcsException>,
    ): Map<GitRepository, MutableCollection<Change>> {
      val result = HashMap<GitRepository, MutableCollection<Change>>()
      for (change in changes) {
        try {
          // note that any path will work, because changes could happen within single vcs root
          val filePath = ChangesUtil.getFilePath(change)

          // the parent paths for calculating roots in order to account for submodules that contribute
          // to the parent change. The path "." is never is valid change, so there should be no problem
          // with it.
          val repository = GitUtil.getRepositoryForFile(project, filePath.parentPath!!)
          val changeList = result.computeIfAbsent(repository) { _ -> mutableListOf() }
          changeList.add(change)
        }
        catch (e: VcsException) {
          exceptions.add(e)
        }
      }
      return result
    }

    fun collectActiveMovementProviders(project: Project): List<GitCheckinExplicitMovementProvider> {
      val allProviders = GitCheckinExplicitMovementProvider.EP_NAME.extensionList
      val enabledProviders = allProviders.filter { it.isEnabled(project) }
      if (enabledProviders.isEmpty()) return emptyList()

      val changes = collectChangesToCommit(ChangeListManager.getInstance(project).allChanges)
      val beforePaths = changes.mapNotNull { it.beforePath }
      val afterPaths = changes.mapNotNull { it.afterPath }

      return enabledProviders.filter { provider ->
        val movements = provider.collectExplicitMovements(project, beforePaths, afterPaths)
        filterExcludedChanges(movements, changes).isNotEmpty()
      }
    }

    fun isCaseOnlyRename(change: ChangedPath): Boolean {
      if (SystemInfo.isFileSystemCaseSensitive) return false
      if (!change.isMove) return false
      val afterPath = change.afterPath!!
      val beforePath = change.beforePath!!
      return GitUtil.isCaseOnlyChange(beforePath.path, afterPath.path)
    }

    fun getLogString(rootPath: String, changes: Collection<ChangedPath>): @NonNls String {
      return GitUtil.getLogString(rootPath, changes, { it.beforePath }, { it.afterPath })
    }
  }

  // used by external plugins
  inner class GitCheckinOptions internal constructor(
    commitPanel: CheckinProjectPanel,
    commitContext: CommitContext,
    showAmendOption: Boolean,
  ) : CheckinChangeListSpecificComponent, RefreshableOnComponent, Disposable {

    private val myOptionsUi = GitCommitOptionsUi(commitPanel, commitContext, showAmendOption)

    init {
      Disposer.register(this, myOptionsUi)
    }

    @Suppress("unused")
    fun getAuthor(): String? {
      val author = myOptionsUi.getAuthor()
      return author?.toString()
    }

    @Suppress("unused")
    fun isAmend(): Boolean {
      return myOptionsUi.amendHandler.commitToAmend is CommitToAmend.Last
    }

    override fun getComponent(): JComponent {
      return myOptionsUi.component
    }

    override fun restoreState() {
      myOptionsUi.restoreState()
    }

    override fun saveState() {
      myOptionsUi.saveState()
    }

    override fun onChangeListSelected(list: LocalChangeList) {
      myOptionsUi.onChangeListSelected(list)
    }

    override fun dispose() {
    }
  }

  open class ChangedPath(
    val beforePath: FilePath?,
    val afterPath: FilePath?,
  ) {

    init {
      assert(beforePath != null || afterPath != null)
    }

    val isMove: Boolean
      get() {
        if (beforePath == null || afterPath == null) return false
        return !ChangesUtil.equalsCaseSensitive(beforePath, afterPath)
      }

    override fun toString(): @NonNls String {
      return String.format("%s -> %s", beforePath, afterPath)
    }
  }

  private class CommitChange(
    beforePath: FilePath?,
    afterPath: FilePath?,
    val beforeRevision: VcsRevisionNumber?,
    val afterRevision: VcsRevisionNumber?,
    val changelistIds: List<String>?,
    val virtualFile: VirtualFile?,
  ) : ChangedPath(beforePath, afterPath) {
    override fun toString(): @NonNls String {
      return super.toString() + ", changelists: " + changelistIds
    }
  }
}