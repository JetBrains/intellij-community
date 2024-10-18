// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.RepoStateException
import com.intellij.dvcs.repo.Repository
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener
import git4idea.util.StringScanner
import org.jetbrains.annotations.NonNls
import java.io.File

/**
 *
 * Reads information about the Git repository from Git service files located in the `.git` folder.
 *
 * NB: works with [File], i.e. reads from disk. Consider using caching.
 * Throws a [RepoStateException] in the case of incorrect Git file format.
 */
internal class GitRepositoryReader(private val project: Project, private val gitFiles: GitRepositoryFiles) {

  fun readState(remotes: Collection<GitRemote>): GitBranchState {
    var headInfo = readHead()

    val branches: GitBranches
    if (headInfo != null && Registry.`is`("git.read.branches.from.disk")) {
      // read branches from disk (.git/refs/ and .git/packed-refs)
      branches = readBranches(remotes)
    }
    else {
      // read branches via git executable
      val pair = readBranchesFromGit(remotes)
      branches = pair.first
      headInfo = pair.second

      if (headInfo is HeadInfo.Unknown) {
        // handle detached head in refrable repos
        val headHash = resolveHeadRevision()
        if (headHash != null) {
          headInfo = HeadInfo.DetachedHead(headHash)
        }
        else {
          LOG.warn(RepoStateException("Unknown repository state in ${gitFiles.rootDir}"))
        }
      }
    }

    val state = readRepositoryState(headInfo is HeadInfo.Branch)
    val headState = parseHeadState(headInfo, state, branches)
    return GitBranchState(headState.currentRevision, headState.currentBranch, state,
                          branches.localBranches, branches.remoteBranches)
  }

  private fun parseHeadState(
    headInfo: HeadInfo,
    state: Repository.State,
    branches: GitBranches,
  ): GitHeadState {
    if (headInfo is HeadInfo.Branch) {
      val currentBranch = fixCurrentBranchCase(headInfo.branch, branches)
      val currentBranchHash = if (currentBranch != null) branches.localBranches[currentBranch] else null
      val currentRevision = currentBranchHash?.asString()

      return GitHeadState(currentBranch, currentRevision)
    }

    val currentBranch = when {
      state == Repository.State.REBASING -> fixCurrentBranchCase(findRebaseBranch(), branches)
      else -> null
    }

    if (headInfo is HeadInfo.DetachedHead) {
      val currentRevision = headInfo.hash.asString()
      return GitHeadState(currentBranch, currentRevision)
    }
    else {
      // headInfo is HeadInfo.Unknown
      if (currentBranch == null) {
        LOG.warn("Couldn't identify neither current branch nor current revision.")
        if (LOG.isDebugEnabled()) {
          LOG.debug("Dumping files in .git/refs/, and the content of .git/packed-refs.")
          GitRefUtil.logDebugAllRefsFiles(gitFiles)
        }
      }
      return GitHeadState(currentBranch, null)
    }
  }

  fun readHooksInfo(): GitHooksInfo {
    val hasCommitHook = isExistingExecutableFile(gitFiles.preCommitHookFile) ||
                        isExistingExecutableFile(gitFiles.commitMsgHookFile)
    val hasPushHook: Boolean = isExistingExecutableFile(gitFiles.prePushHookFile)
    return GitHooksInfo(hasCommitHook, hasPushHook)
  }

  fun hasShallowCommits(): Boolean {
    val shallowFile = gitFiles.shallowFile
    if (!shallowFile.exists()) {
      return false
    }

    return shallowFile.length() > 0
  }

  private fun readRepositoryState(isOnBranch: Boolean): Repository.State {
    if (isMergeInProgress()) {
      return Repository.State.MERGING
    }
    if (isRebaseInProgress()) {
      return Repository.State.REBASING
    }
    if (!isOnBranch) {
      return Repository.State.DETACHED
    }
    if (isCherryPickInProgress()) {
      return Repository.State.GRAFTING
    }
    if (isRevertInProgress()) {
      return Repository.State.REVERTING
    }

    val sequencerTodoBasedState = GitSequencerTodoReader.getTodoBasedState(gitFiles)
    if (sequencerTodoBasedState != null) {
      return sequencerTodoBasedState
    }

    return Repository.State.NORMAL
  }

  private fun findRebaseBranch(): GitLocalBranch? {
    val currentBranch = readRebaseDirBranchFile(gitFiles.rebaseApplyDir)
                        ?: readRebaseDirBranchFile(gitFiles.rebaseMergeDir)
    if (currentBranch != null && currentBranch != DETACHED_HEAD) {
      return GitLocalBranch(currentBranch)
    }
    return null
  }

  /**
   * Unify branch name for CaseInsensitive file systems
   */
  private fun fixCurrentBranchCase(currentBranch: GitLocalBranch?, branches: GitBranches): GitLocalBranch? {
    if (currentBranch == null) return null
    return branches.localBranches.keys.find { branch -> branch == currentBranch } ?: currentBranch
  }

  private fun isMergeInProgress(): Boolean = gitFiles.mergeHeadFile.exists()

  private fun isRebaseInProgress(): Boolean = gitFiles.rebaseApplyDir.exists() || gitFiles.rebaseMergeDir.exists()

  private fun isCherryPickInProgress(): Boolean = gitFiles.cherryPickHead.exists()

  private fun isRevertInProgress(): Boolean = gitFiles.revertHead.exists()

  private fun readPackedBranches(): Map<String, String> {
    val packedRefsFile = gitFiles.packedRefsPath
    if (!packedRefsFile.exists()) {
      return emptyMap()
    }
    try {
      val content = DvcsUtil.tryLoadFile(packedRefsFile, CharsetToolkit.UTF8)

      val result = mutableMapOf<String, String>()
      for (line in LineTokenizer.tokenize(content, false)) {
        val pair = GitRefUtil.parseBranchesLine(line) ?: continue
        result[pair.first] = pair.second
      }
      return result
    }
    catch (_: RepoStateException) {
      return emptyMap()
    }
  }

  private fun readBranches(remotes: Collection<GitRemote>): GitBranches {
    val data = readBranchRefsFromFiles()
    val resolvedRefs = GitRefUtil.resolveRefs(data)
    return createBranchesFromData(remotes, resolvedRefs)
  }

  private fun readBranchRefsFromFiles(): Map<String, String> {
    try {
      // reading from packed-refs first to overwrite values by values from unpacked refs
      val result: MutableMap<String, String> = HashMap<String, String>(readPackedBranches())
      result.putAll(GitRefUtil.readFromRefsFiles(gitFiles.refsHeadsFile, GitBranch.REFS_HEADS_PREFIX, gitFiles))
      result.putAll(GitRefUtil.readFromRefsFiles(gitFiles.refsRemotesFile, GitBranch.REFS_REMOTES_PREFIX, gitFiles))
      return result
    }
    catch (e: Throwable) {
      GitRefUtil.logDebugAllRefsFiles(gitFiles)
      LOG.warn("Error reading refs from files", e)
      return emptyMap()
    }
  }

  private fun readBranchesFromGit(remotes: Collection<GitRemote>): Pair<GitBranches, HeadInfo> {
    try {
      val handler = GitLineHandler(project, gitFiles.rootDir, GitCommand.FOR_EACH_REF)
      handler.isEnableInteractiveCallbacks = false // the method might be called in GitRepository constructor
      handler.addParameters("refs/heads/**", "refs/remotes/**")
      handler.addParameters("--no-color")
      handler.addParameters("--format=%(refname)\t%(objectname)\t%(HEAD)")

      val resolvedRefs = mutableMapOf<String, Hash>()
      var headStateRef: HeadInfo? = null
      handler.addLineListener(object : GitLineHandlerListener {
        private var badLineReported = 0
        override fun onLineAvailable(line: @NlsSafe String, outputType: Key<*>) {
          try {
            if (outputType == ProcessOutputType.STDOUT) {
              val scanner = StringScanner(line)
              val branchRef = scanner.tabToken() ?: return
              val branchHash = GitRefUtil.parseHash(scanner.tabToken()) ?: return
              val isHead = "*" == scanner.line()
              if (isHead) {
                headStateRef = HeadInfo.Branch(GitLocalBranch(branchRef))
              }
              resolvedRefs[branchRef] = branchHash
            }
          }
          catch (e: VcsException) {
            badLineReported++
            if (badLineReported < 5) {
              LOG.warn("Unexpected branch output: $line", e)
            }
          }
        }
      })
      Git.getInstance().runCommand(handler).throwOnError()

      val headState = headStateRef
      val branches = createBranchesFromData(remotes, resolvedRefs)

      if (headState != null) {
        return Pair(branches, headState)
      }

      // handle fresh repository
      if (branches.localBranches.isEmpty()) {
        val currentBranch = resolveCurrentBranch()
        if (currentBranch != null) {
          return Pair(GitBranches(emptyMap(), branches.remoteBranches), HeadInfo.Branch(currentBranch))
        }
      }

      return Pair(branches, HeadInfo.Unknown)
    }
    catch (e: VcsException) {
      LOG.warn(e)
      return Pair(GitBranches(emptyMap(), emptyMap()), HeadInfo.Unknown)
    }
  }

  private fun resolveCurrentBranch(): GitLocalBranch? {
    try {
      val handler = GitLineHandler(project, gitFiles.rootDir, GitCommand.BRANCH)
      handler.isEnableInteractiveCallbacks = false // the method might be called in GitRepository constructor
      handler.addParameters("--show-current")
      handler.addParameters("--no-color")

      val output = Git.getInstance().runCommand(handler).getOutputOrThrow().trim()
      if (output.isEmpty()) return null

      return GitLocalBranch(output)
    }
    catch (e: VcsException) {
      LOG.warn(e)
      return null
    }
  }

  private fun resolveHeadRevision(): Hash? {
    try {
      val handler = GitLineHandler(project, gitFiles.rootDir, GitCommand.REV_PARSE)
      handler.isEnableInteractiveCallbacks = false // the method might be called in GitRepository constructor
      handler.addParameters("--verify")
      handler.addParameters("HEAD^{commit}")

      val output = Git.getInstance().runCommand(handler).getOutputOrThrow().trim()
      if (GitUtil.isHashString(output)) {
        return HashImpl.build(output)
      }
      return null
    }
    catch (e: VcsException) {
      LOG.warn(e)
      return null
    }
  }

  private fun readHead(): HeadInfo? {
    val headContent: String
    try {
      headContent = DvcsUtil.tryLoadFile(gitFiles.headFile, CharsetToolkit.UTF8)
    }
    catch (e: RepoStateException) {
      LOG.warn(e)
      return HeadInfo.Unknown
    }

    if (headContent == "ref: refs/heads/.invalid") {
      return null // 'reftable' format is used
    }

    val hash = GitRefUtil.parseHash(headContent)
    if (hash != null) {
      return HeadInfo.DetachedHead(hash)
    }

    // In theory, 'HEAD' can contain non-local-branch references (ex: a tag)
    // In practice, 'git branch --all' breaks if it does
    val target = GitRefUtil.getTarget(headContent)
    if (target != null) {
      return HeadInfo.Branch(GitLocalBranch(target))
    }

    LOG.warn(RepoStateException("Invalid format of the .git/HEAD file: [$headContent]")) // including "refs/tags/v1"
    return HeadInfo.Unknown
  }

  /**
   * Container to hold two information items: refname from .git/HEAD and is Git on branch.
   */
  private sealed class HeadInfo {
    data object Unknown : HeadInfo()
    data class Branch(val branch: GitLocalBranch) : HeadInfo()
    data class DetachedHead(val hash: Hash) : HeadInfo()
  }

  private class GitBranches(
    val localBranches: Map<GitLocalBranch, Hash>,
    val remoteBranches: Map<GitRemoteBranch, Hash>,
  )

  private class GitHeadState(
    val currentBranch: GitLocalBranch?,
    val currentRevision: String?,
  )

  companion object {
    private val LOG = Logger.getInstance(GitRepositoryReader::class.java)

    private const val DETACHED_HEAD = "detached HEAD"

    private fun isExistingExecutableFile(file: File): Boolean {
      return file.exists() && file.canExecute()
    }

    private fun readRebaseDirBranchFile(rebaseDir: @NonNls File): String? {
      if (rebaseDir.exists()) {
        val headName = File(rebaseDir, "head-name")
        if (headName.exists()) {
          return DvcsUtil.tryLoadFileOrReturn(headName, null, CharsetToolkit.UTF8)
        }
      }
      return null
    }

    private fun createBranchesFromData(
      remotes: Collection<GitRemote>,
      data: Map<String, Hash>,
    ): GitBranches {
      val localBranches: MutableMap<GitLocalBranch, Hash> = HashMap()
      val remoteBranches: MutableMap<GitRemoteBranch, Hash> = HashMap()
      for ((refName, hash) in data.entries) {
        val branch: GitBranch? = parseBranchRef(remotes, refName)
        if (branch is GitLocalBranch) {
          localBranches.put(branch, hash)
        }
        else if (branch is GitRemoteBranch) {
          if (branch.name == GitUtil.ORIGIN_HEAD) {
            continue // skip fake "origin/HEAD" reference
          }
          remoteBranches.put(branch, hash)
        }
        else {
          LOG.warn(String.format("Unexpected ref format: %s, %s", refName, branch))
        }
      }
      return GitBranches(localBranches, remoteBranches)
    }

    fun parseBranchRef(remotes: Collection<GitRemote>, refName: String): GitBranch? {
      if (refName.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
        return GitLocalBranch(refName)
      }
      else if (refName.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
        return GitUtil.parseRemoteBranch(refName, remotes)
      }
      else {
        return null
      }
    }
  }
}

internal object GitSequencerTodoReader {
  private const val REVERT_COMMAND = "revert"
  private const val CHERRY_PICK_COMMAND = "pick"

  private val charsToRead = maxOf(REVERT_COMMAND.length, CHERRY_PICK_COMMAND.length)

  /**
   * The repository can still have cherry-pick/revert in progress with the corresponding HEAD file missing
   * (see [GitRepositoryFiles.cherryPickHead] and [GitRepositoryFiles.revertHead]).
   *
   * It can happen when a conflict occurred while cherry-picking multiple commits and calling "git commit" after the conflict resolution.
   */
  fun getTodoBasedState(gitFiles: GitRepositoryFiles): Repository.State? {
    val sequencerTodoFile = gitFiles.sequencerTodoFile
    if (!sequencerTodoFile.exists()) return null

    return try {
      DvcsUtil.tryOrThrow(
        {
          sequencerTodoFile.reader(Charsets.UTF_8).use { reader ->
            val firstChars = String(FileUtil.loadText(reader, charsToRead))
            when {
              firstChars.startsWith(CHERRY_PICK_COMMAND) -> Repository.State.GRAFTING
              firstChars.startsWith(REVERT_COMMAND) -> Repository.State.REVERTING
              else -> null
            }
          }
        },
        sequencerTodoFile
      )
    }
    catch (_: RepoStateException) {
      null
    }
  }
}