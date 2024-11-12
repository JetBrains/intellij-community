// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.checkin.PostCommitChangeConverter
import com.intellij.openapi.vcs.ex.PartialCommitHelper
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.ThrowableConsumer
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.addIfNotNull
import com.intellij.vcs.commit.AmendCommitAware
import com.intellij.vcs.commit.EditedCommitDetails
import com.intellij.vcs.commit.ToggleAmendCommitOption.Companion.isAmendCommitOptionSupported
import com.intellij.vcs.commit.commitWithoutChangesRoots
import com.intellij.vcs.commit.isAmendCommitMode
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.changes.GitChangeUtils
import git4idea.changes.GitChangeUtils.GitDiffChange
import git4idea.checkin.GitCheckinExplicitMovementProvider.Movement
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitConfigUtil
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
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class GitCheckinEnvironment(private val myProject: Project) : CheckinEnvironment, AmendCommitAware {
  private var myNextCommitAuthor: VcsUser? = null // The author for the next commit
  private var myNextCommitAmend = false // If true, the next commit is amended
  private var myNextCommitAuthorDate: Date? = null
  private var myNextCommitSignOff = false
  private var myNextCommitSkipHook = false
  private var myNextCleanupCommitMessage = false

  override fun isRefreshAfterCommitNeeded(): Boolean {
    return true
  }

  override fun createCommitOptions(commitPanel: CheckinProjectPanel,
                                   commitContext: CommitContext): RefreshableOnComponent {
    return GitCheckinOptions(commitPanel, commitContext, isAmendCommitOptionSupported(commitPanel, this))
  }

  override fun getDefaultMessageFor(filesToCheckin: Array<FilePath>): String? {
    val messages = LinkedHashSet<String>()
    val manager = GitUtil.getRepositoryManager(myProject)
    val repositories = filesToCheckin.mapNotNullTo(HashSet()) { file -> manager.getRepositoryForFileQuick(file) }

    for (repository in repositories) {
      val mergeMsg = repository.repositoryFiles.mergeMessageFile
      val squashMsg = repository.repositoryFiles.squashMessageFile
      try {
        if (!mergeMsg.exists() && !squashMsg.exists()) {
          continue
        }
        val encoding = GitConfigUtil.getCommitEncodingCharset(myProject, repository.root)
        if (mergeMsg.exists()) {
          messages.add(loadMessage(mergeMsg, encoding))
        }
        else {
          messages.add(loadMessage(squashMsg, encoding))
        }
      }
      catch (e: IOException) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Unable to load merge message", e)
        }
      }
    }
    return DvcsUtil.joinMessagesOrNull(messages)
  }

  @Throws(IOException::class)
  private fun loadMessage(messageFile: File, encoding: Charset): String {
    return FileUtil.loadFile(messageFile, encoding)
  }

  override fun getHelpId(): String? {
    return null
  }

  override fun getCheckinOperationName(): String {
    return GitBundle.message("commit.action.name")
  }

  override fun isAmendCommitSupported(): Boolean {
    return amendService.isAmendCommitSupported()
  }

  @Throws(VcsException::class)
  override fun getLastCommitMessage(root: VirtualFile): String {
    return amendService.getLastCommitMessage(root)
  }

  override fun getAmendCommitDetails(root: VirtualFile): CancellablePromise<EditedCommitDetails> {
    return amendService.getAmendCommitDetails(root)
  }

  private val amendService: GitAmendCommitService get() = myProject.getService(GitAmendCommitService::class.java)

  private fun updateState(commitContext: CommitContext) {
    myNextCommitAmend = commitContext.isAmendCommitMode
    myNextCommitSkipHook = commitContext.isSkipHooks
    myNextCommitAuthor = commitContext.commitAuthor
    myNextCommitAuthorDate = commitContext.commitAuthorDate
    myNextCommitSignOff = commitContext.isSignOffCommit
    myNextCleanupCommitMessage = GitCommitTemplateTracker.getInstance(myProject).exists()
  }

  override fun commit(changes: List<Change>,
                      commitMessage: @NonNls String,
                      commitContext: CommitContext,
                      feedback: MutableSet<in String>): List<VcsException> {
    updateState(commitContext)

    val exceptions = mutableListOf<VcsException>()
    val sortedChanges = sortChangesByGitRoot(myProject, changes, exceptions)
    val commitWithoutChangesRoots = commitContext.commitWithoutChangesRoots
    LOG.assertTrue(!sortedChanges.isEmpty() || !commitWithoutChangesRoots.isEmpty(),
                   "Trying to commit an empty list of changes: $changes")

    val commitOptions = createCommitOptions()

    val repositories = collectRepositories(sortedChanges.keys, commitWithoutChangesRoots)
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

    if (commitContext.isPushAfterCommit && exceptions.isEmpty()) {
      GitPushAfterCommitDialog.showOrPush(myProject, repositories)
    }
    return exceptions
  }

  private fun collectRepositories(changesRepositories: Collection<GitRepository>,
                                  noChangesRoots: Collection<VcsRoot>): List<GitRepository> {
    val repositoryManager = GitUtil.getRepositoryManager(myProject)
    val vcs = GitVcs.getInstance(myProject)
    val noChangesRepositories =
      GitUtil.getRepositoriesFromRoots(repositoryManager,
                                       noChangesRoots.mapNotNull { if (it.vcs == vcs) it.path else null })

    return repositoryManager.sortByDependency(ContainerUtil.union(changesRepositories, noChangesRepositories))
  }

  private fun createCommitOptions(): GitCommitOptions {
    return GitCommitOptions(myNextCommitAmend, myNextCommitSignOff, myNextCommitSkipHook, myNextCommitAuthor, myNextCommitAuthorDate,
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
    VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(root)
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


    private fun commitRepository(repository: GitRepository,
                                 changes: Collection<CommitChange>,
                                 message: @NonNls String,
                                 commitContext: CommitContext,
                                 commitOptions: GitCommitOptions): List<VcsException> {
      val exceptions = mutableListOf<VcsException>()
      val project = repository.project
      val root = repository.root

      try {
        // Stage partial changes
        val (partialCommitHelpers, partialChanges) = addPartialChangesToIndex(repository, changes)
        val changedWithIndex = HashSet(partialChanges)

        // Stage case-only renames
        val caseOnlyRenameChanges = addCaseOnlyRenamesToIndex(repository, changes, changedWithIndex, exceptions)
        if (!exceptions.isEmpty()) return exceptions
        changedWithIndex.addAll(caseOnlyRenameChanges)

        runWithMessageFile(project, root, message) { messageFile: File ->
          exceptions.addAll(commitUsingIndex(project, repository, changes, changedWithIndex,
                                             messageFile, commitOptions))
        }
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
    fun commitUsingIndex(project: Project,
                         repository: GitRepository,
                         rootChanges: Collection<ChangedPath>,
                         changedWithIndex: Set<ChangedPath>,
                         messageFile: File,
                         commitOptions: GitCommitOptions): List<VcsException> {
      val exceptions = mutableListOf<VcsException>()
      try {
        val added: Set<FilePath> = rootChanges.mapNotNullTo(HashSet()) { it.afterPath }
        val removed: Set<FilePath> = rootChanges.mapNotNullTo(HashSet()) { it.beforePath }

        val root = repository.root
        val rootPath = root.path

        val unmergedFiles = GitChangeUtils.getUnmergedFiles(repository)
        if (!unmergedFiles.isEmpty()) {
          throw VcsException(GitBundle.message("error.commit.cant.commit.with.unmerged.paths"))
        }

        // Check what is staged besides our changes
        val stagedChanges = GitChangeUtils.getStagedChanges(project, root)
        LOG.debug("Found staged changes: " + GitUtil.getLogStringGitDiffChanges(rootPath, stagedChanges))
        val excludedStagedChanges = mutableListOf<ChangedPath>()
        val excludedStagedAdditions = mutableListOf<FilePath>()
        processExcludedPaths(stagedChanges, added, removed) { before, after ->
          if (before != null || after != null) excludedStagedChanges.add(ChangedPath(before, after))
          if (before == null && after != null) excludedStagedAdditions.add(after)
        }

        // Find files with 'AD' status, we will not be able to restore them after using 'git add' command,
        // getting "pathspec 'file.txt' did not match any files" error (and preventing us from adding other files).
        val unstagedChanges = GitChangeUtils.getUnstagedChanges(project, root, excludedStagedAdditions, false)
        LOG.debug("Found unstaged changes: " + GitUtil.getLogStringGitDiffChanges(rootPath, unstagedChanges))
        val excludedUnstagedDeletions = HashSet<FilePath>()
        processExcludedPaths(unstagedChanges, added, removed) { before, after ->
          if (before != null && after == null) excludedUnstagedDeletions.add(before)
        }

        if (!excludedStagedChanges.isEmpty()) {
          // Reset staged changes which are not selected for commit
          LOG.info("Staged changes excluded for commit: " + getLogString(rootPath, excludedStagedChanges))
          resetExcluded(project, root, excludedStagedChanges)
        }
        try {
          val alreadyHandledPaths = getPaths(changedWithIndex)
          // Stage what else is needed to commit
          val toAdd = HashSet(added)
          toAdd.removeAll(alreadyHandledPaths)

          val toRemove = HashSet(removed)
          toRemove.removeAll(toAdd)
          toRemove.removeAll(alreadyHandledPaths)

          LOG.debug(String.format("Updating index: added: %s, removed: %s", toAdd, toRemove))
          updateIndex(project, root, toAdd, toRemove, exceptions)
          if (!exceptions.isEmpty()) return exceptions


          // Commit the staging area
          LOG.debug("Performing commit...")
          val committer = GitRepositoryCommitter(repository, commitOptions)
          committer.commitStaged(messageFile)
        }
        finally {
          // Stage back the changes unstaged before commit
          if (!excludedStagedChanges.isEmpty()) {
            restoreExcluded(project, root, excludedStagedChanges, excludedUnstagedDeletions)
          }
        }
      }
      catch (e: VcsException) {
        exceptions.add(e)
      }
      return exceptions
    }

    @Throws(VcsException::class)
    private fun addPartialChangesToIndex(repository: GitRepository,
                                         changes: Collection<CommitChange>): Pair<List<PartialCommitHelper>, List<CommitChange>> {
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

    private fun convertDocumentContentToBytes(repository: GitRepository,
                                              documentContent: @NonNls String,
                                              file: VirtualFile): ByteArray {
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
    fun convertDocumentContentToBytesWithBOM(repository: GitRepository,
                                             documentContent: @NonNls String,
                                             file: VirtualFile): ByteArray {
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


    private fun addCaseOnlyRenamesToIndex(repository: GitRepository,
                                          changes: Collection<CommitChange>,
                                          alreadyProcessed: Set<CommitChange>,
                                          exceptions: MutableList<in VcsException>): List<CommitChange> {
      if (SystemInfo.isFileSystemCaseSensitive) return emptyList()

      val caseOnlyRenames = changes.filter { change -> !alreadyProcessed.contains(change) && isCaseOnlyRename(change) }
      if (caseOnlyRenames.isEmpty()) return emptyList()

      LOG.info("Committing case only rename: " + getLogString(repository.root.path, caseOnlyRenames) +
               " in " + DvcsUtil.getShortRepositoryName(repository))

      val pathsToAdd = caseOnlyRenames.map { it.afterPath!! }
      val pathsToDelete = caseOnlyRenames.map { it.beforePath!! }

      LOG.debug(String.format("Updating index for case only changes: added: %s,\n removed: %s", pathsToAdd, pathsToDelete))
      updateIndex(repository.project, repository.root, pathsToAdd, pathsToDelete, exceptions)

      return caseOnlyRenames
    }

    private fun isCaseOnlyRename(change: ChangedPath): Boolean {
      if (SystemInfo.isFileSystemCaseSensitive) return false
      if (!change.isMove) return false
      val afterPath = change.afterPath!!
      val beforePath = change.beforePath!!
      return GitUtil.isCaseOnlyChange(beforePath.path, afterPath.path)
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

    private fun processExcludedPaths(changes: Collection<GitDiffChange>,
                                     added: Set<FilePath>,
                                     removed: Set<FilePath>,
                                     function: (before: FilePath?, after: FilePath?) -> Unit) {
      for (change in changes) {
        var before = change.beforePath
        var after = change.afterPath
        if (removed.contains(before)) before = null
        if (added.contains(after)) after = null
        function(before, after)
      }
    }

    private fun getLogString(root: String, changes: Collection<ChangedPath>): @NonNls String {
      return GitUtil.getLogString(root, changes, { it.beforePath }, { it.afterPath })
    }

    private fun commitExplicitRenames(repository: GitRepository,
                                      changes: Collection<CommitChange>,
                                      message: @NonNls String,
                                      commitOptions: GitCommitOptions): Pair<Collection<CommitChange>, List<VcsException>> {
      val project = repository.project
      val root = repository.root

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

        runWithMessageFile(project, root, newMessage) { moveMessageFile ->
          exceptions.addAll(commitUsingIndex(project, repository, movedChanges, HashSet(movedChanges),
                                             moveMessageFile, commitOptions))
        }

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
    private fun addExplicitMovementsToIndex(repository: GitRepository,
                                            changes: Collection<CommitChange>,
                                            explicitMoves: Collection<Movement>): Pair<List<CommitChange>, List<CommitChange>>? {
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

    private fun filterExcludedChanges(explicitMoves: Collection<Movement>,
                                      changes: Collection<CommitChange>): List<Movement> {
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


    @Throws(VcsException::class)
    private fun resetExcluded(project: Project,
                              root: VirtualFile,
                              changes: Collection<ChangedPath>) {
      val allPaths: MutableSet<FilePath> = CollectionFactory.createCustomHashingStrategySet(ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY)
      for (change in changes) {
        ContainerUtil.addIfNotNull(allPaths, change.afterPath)
        ContainerUtil.addIfNotNull(allPaths, change.beforePath)
      }

      for (paths in VcsFileUtil.chunkPaths(root, allPaths)) {
        val handler = GitLineHandler(project, root, GitCommand.RESET)
        handler.endOptions()
        handler.addParameters(paths)
        Git.getInstance().runCommand(handler).throwOnError()
      }
    }

    private fun restoreExcluded(project: Project,
                                root: VirtualFile,
                                changes: Collection<ChangedPath>,
                                unstagedDeletions: Set<FilePath>) {
      val restoreExceptions = mutableListOf<VcsException>()

      val toAdd = HashSet<FilePath>()
      val toRemove = HashSet<FilePath>()

      for (change in changes) {
        if (addAsCaseOnlyRename(project, root, change, restoreExceptions)) continue

        if (change.beforePath == null && unstagedDeletions.contains(change.afterPath)) {
          // we can't restore ADDED-DELETED files
          LOG.info("Ignored added-deleted staged change in " + change.afterPath)
          continue
        }

        ContainerUtil.addIfNotNull(toAdd, change.afterPath)
        ContainerUtil.addIfNotNull(toRemove, change.beforePath)
      }
      toRemove.removeAll(toAdd)

      LOG.debug(String.format("Restoring staged changes after commit: added: %s, removed: %s", toAdd, toRemove))
      updateIndex(project, root, toAdd, toRemove, restoreExceptions)

      for (e in restoreExceptions) {
        LOG.warn(e)
      }
    }

    private fun addAsCaseOnlyRename(project: Project, root: VirtualFile, change: ChangedPath,
                                    exceptions: MutableList<in VcsException>): Boolean {
      try {
        if (!isCaseOnlyRename(change)) return false

        val beforePath = change.beforePath!!
        val afterPath = change.afterPath!!

        LOG.debug(String.format("Restoring staged case-only rename after commit: %s", change))
        val h = GitLineHandler(project, root, GitCommand.MV)
        h.addParameters("-f", beforePath.path, afterPath.path)
        Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError()
        return true
      }
      catch (e: VcsException) {
        exceptions.add(e)
        return false
      }
    }

    /**
     * Update index (delete and remove files)
     *
     * @param project    the project
     * @param root       a vcs root
     * @param added      added/modified files to commit
     * @param removed    removed files to commit
     * @param exceptions a list of exceptions to update
     */
    private fun updateIndex(project: Project,
                            root: VirtualFile,
                            added: Collection<FilePath>,
                            removed: Collection<FilePath>,
                            exceptions: MutableList<in VcsException>) {
      if (!removed.isEmpty()) {
        try {
          GitFileUtils.deletePaths(project, root, removed, "--ignore-unmatch", "--cached", "-r")
        }
        catch (ex: VcsException) {
          exceptions.add(ex)
        }
      }
      if (!added.isEmpty()) {
        try {
          GitFileUtils.addPathsForce(project, root, added)
        }
        catch (ex: VcsException) {
          exceptions.add(ex)
        }
      }
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
      val file = FileUtil.createTempFile(GIT_COMMIT_MSG_FILE_PREFIX, GIT_COMMIT_MSG_FILE_EXT)
      @Suppress("SSBasedInspection")
      file.deleteOnExit()

      val encoding = GitConfigUtil.getCommitEncodingCharset(project, root)
      OutputStreamWriter(FileOutputStream(file), encoding).use { out ->
        out.write(message)
      }
      return file
    }

    @Throws(VcsException::class)
    @JvmStatic
    fun runWithMessageFile(project: Project, root: VirtualFile, message: @NonNls String,
                           task: ThrowableConsumer<in File, out VcsException>) {
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

    private fun sortChangesByGitRoot(project: Project,
                                     changes: List<Change>,
                                     exceptions: MutableList<in VcsException>): Map<GitRepository, MutableCollection<Change>> {
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
  }

  // used by external plugins
  inner class GitCheckinOptions internal constructor(commitPanel: CheckinProjectPanel,
                                                     commitContext: CommitContext,
                                                     showAmendOption: Boolean
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
      return myOptionsUi.amendHandler.isAmendCommitMode
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

  open class ChangedPath(val beforePath: FilePath?,
                         val afterPath: FilePath?) {

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

  private class CommitChange(beforePath: FilePath?,
                             afterPath: FilePath?,
                             val beforeRevision: VcsRevisionNumber?,
                             val afterRevision: VcsRevisionNumber?,
                             val changelistIds: List<String>?,
                             val virtualFile: VirtualFile?) : ChangedPath(beforePath, afterPath) {
    override fun toString(): @NonNls String {
      return super.toString() + ", changelists: " + changelistIds
    }
  }
}
