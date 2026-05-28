// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.config.GitVersionSpecialty.CAT_FILE_SUPPORTS_FILTERS
import git4idea.config.GitVersionSpecialty.CAT_FILE_SUPPORTS_TEXTCONV
import git4idea.index.GitIndexUtil
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.ByteArrayInputStream

object GitFileUtils {
  private val LOG = logger<GitFileUtils>()

  /** Commands that support `--pathspec-from-file`; see [git4idea.config.GitVersionSpecialty.PATHSPEC_FROM_FILE_SUPPORTED]. */
  private val PATHSPEC_FROM_FILE_SUPPORTED_COMMANDS =
    setOf(GitCommand.ADD, GitCommand.RM, GitCommand.CHECKOUT, GitCommand.RESET, GitCommand.RESTORE)
  const val READ_CONTENT_WITH: String = "git.read.content.with"

  @JvmStatic
  @Throws(VcsException::class)
  fun deletePaths(project: Project, root: VirtualFile, files: Collection<FilePath>, vararg additionalOptions: @NonNls String) {
    executeForFiles(project, root, GitCommand.RM, files) {
      addParameters(*additionalOptions)
    }
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun deleteFilesFromCache(project: Project, root: VirtualFile, files: Collection<VirtualFile>) {
    val paths = files.map { VcsUtil.getFilePath(it) }
    deletePaths(project, root, paths, "--cached")
    updateUntrackedFilesHolderOnFileRemove(project, root, paths)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addFiles(project: Project, root: VirtualFile, vararg files: VirtualFile) {
    addFiles(project, root, files.toList())
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addFiles(project: Project, root: VirtualFile, files: Collection<VirtualFile>) {
    val paths = files.map { VcsUtil.getFilePath(it) }
    addPaths(project, root, paths)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addFilesForce(project: Project, root: VirtualFile, files: Collection<VirtualFile>) {
    val paths = files.map { VcsUtil.getFilePath(it) }
    addPathsForce(project, root, paths)
  }

  private fun updateUntrackedFilesHolderOnFileAdd(project: Project, root: VirtualFile, addedFiles: Collection<FilePath>) {
    val repository = getRepositoryOrLog(project, root) ?: return
    repository.untrackedFilesHolder.removeUntracked(addedFiles)
  }

  private fun updateIgnoredFilesHolderOnFileAdd(project: Project, root: VirtualFile, addedFiles: Collection<FilePath>) {
    val repository = getRepositoryOrLog(project, root) ?: return
    repository.ignoredFilesHolder.removeIgnoredFiles(addedFiles)
  }

  private fun updateUntrackedFilesHolderOnFileRemove(project: Project, root: VirtualFile, removedFiles: Collection<FilePath>) {
    val repository = getRepositoryOrLog(project, root) ?: return
    repository.untrackedFilesHolder.addUntracked(removedFiles)
  }

  private fun updateUntrackedFilesHolderOnFileReset(project: Project, root: VirtualFile, resetFiles: Collection<FilePath>) {
    val repository = getRepositoryOrLog(project, root) ?: return
    repository.untrackedFilesHolder.markPossiblyUntracked(resetFiles)
  }

  private fun getRepositoryOrLog(project: Project, root: VirtualFile): GitRepository? {
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root)
    if (repository == null) {
      LOG.warn("Repository not found for root ${root.presentableUrl}")
    }
    return repository
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addPaths(project: Project, root: VirtualFile, paths: Collection<FilePath>) {
    addPaths(project, root, paths, false)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addPaths(project: Project, root: VirtualFile, files: Collection<FilePath>, force: Boolean) {
    addPaths(project, root, files, force, !force)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addPathsToIndex(project: Project, root: VirtualFile, files: Collection<FilePath>) {
    for (file in files) {
      GitIndexUtil.write(project, root, file, ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY), false, true)
    }
    updateAndRefresh(project, root, files, false)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addPaths(
    project: Project,
    root: VirtualFile,
    files: Collection<FilePath>,
    force: Boolean,
    filterOutIgnored: Boolean,
    vararg additionalOptions: String,
  ) {
    val paths = VcsFileUtil.toRelativePaths(root, files)
    val effectivePaths = if (filterOutIgnored) excludeIgnoredFiles(project, root, paths) else paths

    executeForPaths(project, root, GitCommand.ADD, effectivePaths) {
      addParameters("--ignore-errors", "-A")
      if (force) addParameters("-f")
      addParameters(*additionalOptions)
    }

    updateAndRefresh(project, root, files, force)
  }

  /**
   * @param project    the project
   * @param root       a vcs root
   * @param toAdd      added/modified files to commit
   * @param toRemove   removed files to commit
   * @param exceptions a list of exceptions to update
   */
  @JvmStatic
  fun stageForCommit(
    project: Project,
    root: VirtualFile,
    toAdd: Collection<FilePath>,
    toRemove: Collection<FilePath>,
    exceptions: MutableList<in VcsException>,
  ) {
    if (toRemove.isNotEmpty()) {
      try {
        deletePaths(project, root, toRemove, "--ignore-unmatch", "--cached", "-r")
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }
    if (toAdd.isNotEmpty()) {
      try {
        addPathsForce(project, root, toAdd)
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }
  }

  private fun updateAndRefresh(project: Project, root: VirtualFile, files: Collection<FilePath>, updateIgnoredHolders: Boolean) {
    updateUntrackedFilesHolderOnFileAdd(project, root, files)
    if (updateIgnoredHolders) {
      updateIgnoredFilesHolderOnFileAdd(project, root, files)
    }
    GitIndexFileSystemRefresher.refreshFilePaths(project, files)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun addPathsForce(project: Project, root: VirtualFile, files: Collection<FilePath>) {
    addPaths(project, root, files, true, false)
  }

  private fun excludeIgnoredFiles(project: Project, root: VirtualFile, paths: List<String>): List<String> {
    val handler = GitLineHandler(project, root, GitCommand.CHECK_IGNORE).apply {
      setSilent(true)
      addParameters("--stdin")
      addParameters("-z")
      setInputProcessor(GitHandlerInputProcessorUtil.writeLines(paths, "\u0000", charset, false))
    }
    val output = Git.getInstance().runCommand(handler).getOutputOrThrow(1)
    val ignoredPaths = output.split('\u0000').toHashSet()
    return paths.filterNot { it in ignoredPaths }
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun resetPaths(project: Project, root: VirtualFile, files: Collection<FilePath>) {
    executeForFiles(project, root, GitCommand.RESET, files)
    updateUntrackedFilesHolderOnFileReset(project, root, files)
    GitIndexFileSystemRefresher.refreshFilePaths(project, files)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun revertUnstagedPaths(project: Project, root: VirtualFile, files: List<FilePath>) {
    executeForFiles(project, root, GitCommand.CHECKOUT, files)
  }

  @JvmStatic
  @Throws(VcsException::class)
  fun restoreStagedAndWorktree(project: Project, root: VirtualFile, files: List<FilePath>, source: String) {
    executeForFiles(project, root, GitCommand.RESTORE, files) {
      addParameters("--staged", "--worktree", "--source=$source")
    }
  }

  /**
   * Get file content for the specific revision
   *
   * @param project          the project
   * @param root             the vcs root
   * @param revisionOrBranch the revision to find path in or branch
   * @return the content of file if file is found
   * @throws VcsException if there is a problem with running git
   */
  @JvmStatic
  @RequiresBackgroundThread
  @Throws(VcsException::class)
  fun getFileContent(project: Project, root: VirtualFile, @NonNls revisionOrBranch: String, @NonNls relativePath: String): ByteArray {
    val h = GitBinaryHandler(project, root, GitCommand.CAT_FILE)
    h.setSilent(true)
    addTextConvParameters(project, h, true)
    h.addParameters("$revisionOrBranch:$relativePath")
    return h.run()
  }

  @JvmStatic
  fun addTextConvParameters(project: Project, h: GitBinaryHandler, addp: Boolean) {
    addTextConvParameters(GitExecutableManager.getInstance().tryGetVersion(project, h.executable), h, addp)
  }

  @JvmStatic
  fun addTextConvParameters(version: GitVersion?, h: GitBinaryHandler, addp: Boolean) {
    val effectiveVersion = version ?: GitVersion.NULL
    val mode = AdvancedSettings.getEnum(READ_CONTENT_WITH, GitTextConvMode::class.java)

    if (mode == GitTextConvMode.FILTERS && CAT_FILE_SUPPORTS_FILTERS.existsIn(effectiveVersion)) {
      h.addParameters("--filters")
      return
    }
    if (mode == GitTextConvMode.TEXTCONV && CAT_FILE_SUPPORTS_TEXTCONV.existsIn(effectiveVersion)) {
      h.addParameters("--textconv")
      return
    }

    // '-p' is not needed with '--batch' parameter
    if (addp) {
      h.addParameters("-p")
    }
  }

  /**
   * Runs [command] against [files] in [root], handling arbitrarily large path lists.
   *
   * Paths are fed via `--pathspec-from-file` when all of the following hold:
   * [git4idea.config.GitVersionSpecialty.PATHSPEC_FROM_FILE_SUPPORTED] is satisfied,
   * the `git.use.pathspec.from.file` registry key is enabled,
   * and [command] is listed in [PATHSPEC_FROM_FILE_SUPPORTED_COMMANDS].
   * Otherwise, paths are chunked and the command is invoked multiple times.
   */
  @JvmStatic
  @JvmOverloads
  @Throws(VcsException::class)
  @ApiStatus.Internal
  fun executeForFiles(
    project: Project,
    root: VirtualFile,
    command: GitCommand,
    files: Collection<FilePath>,
    setup: GitLineHandler.() -> Unit = {},
  ) {
    executeForPaths(project, root, command, VcsFileUtil.toRelativePaths(root, files), setup)
  }

  private fun executeForPaths(
    project: Project,
    root: VirtualFile,
    command: GitCommand,
    paths: List<String>,
    setup: GitLineHandler.() -> Unit = {},
  ) {
    if (paths.isEmpty()) return

    val pathspecFromFileEnabled = GitVersionSpecialty.PATHSPEC_FROM_FILE_SUPPORTED.existsIn(project) && Registry.`is`("git.use.pathspec.from.file")
    if (pathspecFromFileEnabled && command in PATHSPEC_FROM_FILE_SUPPORTED_COMMANDS) {
      val handler = GitLineHandler(project, root, command).apply {
        setup()
        addParameters("--pathspec-from-file=-", "--pathspec-file-nul")
        setInputProcessor(GitHandlerInputProcessorUtil.writeLines(paths, "\u0000", charset, false))
      }
      Git.getInstance().runCommand(handler).throwOnError()
    }
    else {
      if (pathspecFromFileEnabled) {
        LOG.debug("Command '$command' does not support --pathspec-from-file, falling back to chunked arguments")
      }
      for (paths in VcsFileUtil.chunkArguments(paths)) {
        val handler = GitLineHandler(project, root, command).apply {
          setup()
          endOptions()
          addParameters(paths)
        }
        Git.getInstance().runCommand(handler).throwOnError()
      }
    }
  }
}
