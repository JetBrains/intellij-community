// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.RepoStateException
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.LineTokenizer
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.vcs.log.Hash
import git4idea.*
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
    val branches = readBranches(remotes)
    val localBranches = branches.localBranches

    val headInfo = readHead()
    val state = readRepositoryState(headInfo is HeadInfo.Branch)

    var currentBranch: GitLocalBranch?
    var currentRevision: String?
    if (headInfo !is HeadInfo.Branch || !localBranches.isEmpty()) {
      currentBranch = findCurrentBranch(headInfo, state, localBranches.keys)
      currentRevision = getCurrentRevision(headInfo, if (currentBranch == null) null else localBranches[currentBranch])
    }
    else {
      currentBranch = headInfo.branch
      currentRevision = null
    }
    if (currentBranch == null && currentRevision == null) {
      LOG.warn("Couldn't identify neither current branch nor current revision. Ref specified in .git/HEAD: [" + headInfo + "]")
      LOG.debug("Dumping files in .git/refs/, and the content of .git/packed-refs. Debug enabled: " + LOG.isDebugEnabled())
      GitRefUtil.logDebugAllRefsFiles(gitFiles)
    }
    return GitBranchState(currentRevision, currentBranch, state, localBranches, branches.remoteBranches)
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

  private fun findCurrentBranch(
    headInfo: HeadInfo,
    state: Repository.State,
    localBranches: Set<GitLocalBranch>,
  ): GitLocalBranch? {
    val currentBranchName = findCurrentBranchName(state, headInfo) ?: return null
    val currentBranch = localBranches.find { branch ->
      GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(branch.fullName, currentBranchName)
    }
    return currentBranch ?: GitLocalBranch(currentBranchName)
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
    return Repository.State.NORMAL
  }

  private fun findCurrentBranchName(state: Repository.State, headInfo: HeadInfo): String? {
    var currentBranch: String? = null
    if (headInfo is HeadInfo.Branch) {
      currentBranch = headInfo.branch.name
    }
    else if (state == Repository.State.REBASING) {
      currentBranch = tryFindRebaseBranch()
    }
    return GitRefUtil.addRefsHeadsPrefixIfNeeded(currentBranch)
  }

  private fun tryFindRebaseBranch(): String? {
    var currentBranch: String? = readRebaseDirBranchFile(gitFiles.rebaseApplyDir)
    if (currentBranch == null) {
      currentBranch = readRebaseDirBranchFile(gitFiles.rebaseMergeDir)
    }
    return if (currentBranch == null || currentBranch == DETACHED_HEAD) null else currentBranch
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
      result.remove(GitBranch.REFS_REMOTES_PREFIX + GitUtil.ORIGIN_HEAD)
      return result
    }
    catch (e: Throwable) {
      GitRefUtil.logDebugAllRefsFiles(gitFiles)
      LOG.warn("Error reading refs from files", e)
      return emptyMap()
    }
  }

  private fun readHead(): HeadInfo {
    val headContent: String
    try {
      headContent = DvcsUtil.tryLoadFile(gitFiles.headFile, CharsetToolkit.UTF8)
    }
    catch (e: RepoStateException) {
      LOG.warn(e)
      return HeadInfo.Unknown
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

  companion object {
    private val LOG = Logger.getInstance(GitRepositoryReader::class.java)

    private const val DETACHED_HEAD = "detached HEAD"

    private fun isExistingExecutableFile(file: File): Boolean {
      return file.exists() && file.canExecute()
    }

    private fun getCurrentRevision(headInfo: HeadInfo, currentBranchHash: Hash?): String? {
      var currentRevision: String?
      if (headInfo !is HeadInfo.Branch) {
        currentRevision = (headInfo as? HeadInfo.DetachedHead)?.hash?.asString()
      }
      else if (currentBranchHash == null) {
        currentRevision = null
      }
      else {
        currentRevision = currentBranchHash.asString()
      }
      return currentRevision
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
