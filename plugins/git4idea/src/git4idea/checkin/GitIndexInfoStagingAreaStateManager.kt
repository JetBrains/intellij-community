// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener
import git4idea.i18n.GitBundle
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepository
import git4idea.util.StringScanner
import org.jetbrains.annotations.NonNls

internal class GitIndexInfoStagingAreaStateManager(val repository: GitRepository) : GitStagingAreaStateManager {
  val project = repository.project
  val root = repository.root
  private var savedStagedChanges: List<StagedChange.Staged> = emptyList()

  override fun prepareStagingArea(toAdd: Set<FilePath>, toRemove: Set<FilePath>) {
    val pathsToIgnore = toAdd.union(toRemove)
    val rootPath = repository.root.path
    val all = listStagedChanges(repository)
    val unmerged = all.filterIsInstance<StagedChange.Unmerged>()
    if (unmerged.isNotEmpty()) {
      throw VcsException(GitBundle.message("error.commit.cant.commit.with.unmerged.paths"))
    }

    val staged = all.filterIsInstance<StagedChange.Staged>()
    LOG.debug { "Found staged changes: " + getStagedLogString(rootPath, staged) }

    savedStagedChanges = staged.filter { it.filePath !in pathsToIgnore }
    LOG.info("Staged changes excluded for commit: " + getStagedLogString(rootPath, savedStagedChanges))

    resetExcluded()
  }

  override fun restore() {
    val updateIndexLines = savedStagedChanges.mapNotNull { createUpdateIndexLine(it, false) }
    if (updateIndexLines.isNotEmpty()) {
      try {
        LOG.debug { "Restoring staged changes after commit" }
        val handler = GitLineHandler(project, root, GitCommand.UPDATE_INDEX).apply {
          addParameters("--index-info")
          setInputProcessor(GitHandlerInputProcessorUtil.writeLines(updateIndexLines, charset))
        }
        Git.getInstance().runCommand(handler).throwOnError()
      }
      catch (e: VcsException) {
        LOG.warn(e)
      }
    }
  }

  private fun listStagedChanges(repository: GitRepository): List<StagedChange> {
    val result = mutableListOf<StagedChange>()
    val h = GitLineHandler(repository.project, repository.root, GitCommand.STATUS).apply {
      addParameters("--porcelain=2")
      addParameters("--no-renames")
      addParameters("--no-ahead-behind")
      addParameters("--no-show-stash")
      addParameters("--untracked-files=no")
      addParameters("--ignored=no")
      endOptions()

      addLineListener(GitLineHandlerListener { line, outputType ->
        if (outputType === ProcessOutputTypes.STDOUT) {
          parseStatusLinePorcelainV2(line, result)
        }
      })
    }
    Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError()
    return result
  }

  private fun parseStatusLinePorcelainV2(line: String, result: MutableList<StagedChange>) {
    try {
      val s = StringScanner(line)
      when (s.spaceToken()) {
        STATUS_ORDINARY -> {
          parseOrdinary(s, line, result)
        }
        STATUS_UNMERGED -> {
          parseUnmerged(s, result)
        }
        else -> {
          LOG.debug { "Unknown status line: $line" }
        }
      }
    }
    catch (e: VcsException) {
      LOG.error(e)
    }
  }

  private fun parseUnmerged(
    s: StringScanner,
    result: MutableList<StagedChange>,
  ) {
    s.spaceToken() // <XY> status
    val isSubmodule = s.spaceToken() != SUBMODULE_NONE // <sub>
    s.spaceToken() // <m1> file mode
    s.spaceToken() // <m2> file mode
    s.spaceToken() // <m3> file mode
    s.spaceToken() // <mW> file mode
    s.spaceToken() // <h1> blob blob
    s.spaceToken() // <h2> blob hash
    s.spaceToken() // <h3> blob hash
    val filePath = s.line()

    val path = VcsUtil.getFilePath(root, GitUtil.unescapePath(filePath))
    result.add(StagedChange.Unmerged(filePath = path,
                                     isSubmodule = isSubmodule))
  }

  private fun parseOrdinary(
    s: StringScanner,
    line: String,
    result: MutableList<StagedChange>,
  ) {
    val status = s.spaceToken() // <XY> status
    val isSubmodule = s.spaceToken() != SUBMODULE_NONE // <sub>
    val headIsExecutable = s.spaceToken() == GitIndexUtil.EXECUTABLE_MODE // <mH> file mode
    val stagedIsExecutable = s.spaceToken() == GitIndexUtil.EXECUTABLE_MODE // <mI> file mode
    s.spaceToken() // <mW> file mode
    val headHash = HashImpl.build(s.spaceToken()) // <hH> blob hash
    val stagedHash = HashImpl.build(s.spaceToken()) // <hI> blob hash
    val filePath = s.line()

    if (status.length != 2) {
      LOG.warn("Unknown file status: $line")
      return
    }

    val stagedStatus = status[0]
    val isFileStaged = stagedStatus != '.'
    if (isFileStaged) {
      val path = VcsUtil.getFilePath(root, GitUtil.unescapePath(filePath))
      result.add(StagedChange.Staged(filePath = path,
                                     isSubmodule = isSubmodule,
                                     headHash = headHash,
                                     stagedHash = stagedHash,
                                     headIsExecutable = headIsExecutable,
                                     stagedIsExecutable = stagedIsExecutable))
    }
  }

  private fun resetExcluded() {
    val updateIndexLines = savedStagedChanges.mapNotNull { createUpdateIndexLine(it, true) }
    if (updateIndexLines.isNotEmpty()) {
      try {
        LOG.debug { "Resetting staged changes after commit" }
        val handler = GitLineHandler(project, root, GitCommand.UPDATE_INDEX)
        handler.addParameters("--index-info")
        handler.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(updateIndexLines, handler.charset))
        Git.getInstance().runCommand(handler).throwOnError()
      }
      catch (e: VcsException) {
        LOG.warn(e)
      }
    }
  }

  private fun createUpdateIndexLine(change: StagedChange.Staged, toHead: Boolean): String? {
    val relativePath = VcsFileUtil.relativePath(root, change.filePath) ?: return null

    val (hash, isExecutable) = if (toHead) {
      change.headHash to change.headIsExecutable
    }
    else {
      change.stagedHash to change.stagedIsExecutable
    }

    // NULL_HASH means "delete from index" for update-index --index-info
    if (hash == GitIndexUtil.NULL_HASH) {
      return "0 $hash\t$relativePath"
    }

    val mode = resolveMode(change.isSubmodule, isExecutable)
    return formatIndexInfo(mode, hash.asString(), relativePath)
  }

  private fun resolveMode(isSubmodule: Boolean, isExecutable: Boolean): String {
    return when {
      isSubmodule -> GitIndexUtil.SUBMODULE_MODE
      isExecutable -> GitIndexUtil.EXECUTABLE_MODE
      else -> GitIndexUtil.DEFAULT_MODE
    }
  }

  private fun formatIndexInfo(mode: String, hash: String, relativePath: String): String {
    // format: "<mode> <hash> <stage>\t<path>"
    return "$mode $hash 0\t$relativePath"
  }

  private fun getStagedLogString(rootPath: String, changes: Collection<StagedChange>): @NonNls String =
    changes.joinToString(", ") { GitUtil.getLogString(rootPath, it.filePath) }

  private sealed class StagedChange(
    val filePath: FilePath,
    val isSubmodule: Boolean,
  ) {
    class Unmerged(
      filePath: FilePath,
      isSubmodule: Boolean,
    ) : StagedChange(filePath, isSubmodule)

    class Staged(
      filePath: FilePath,
      isSubmodule: Boolean,
      val headHash: Hash,
      val stagedHash: Hash,
      val headIsExecutable: Boolean,
      val stagedIsExecutable: Boolean,
    ) : StagedChange(filePath, isSubmodule)
  }

  companion object {
    private const val STATUS_ORDINARY = "1"
    private const val STATUS_UNMERGED = "u"
    private const val SUBMODULE_NONE = "N..."
    private val LOG = logger<GitIndexInfoStagingAreaStateManager>()
  }
}

