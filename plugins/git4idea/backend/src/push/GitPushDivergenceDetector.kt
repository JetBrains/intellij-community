// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.push.PushDivergence
import com.intellij.dvcs.push.VcsError
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.Hash
import git4idea.GitBranch
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<GitPushDivergenceDetector>()

/**
 * Finds the commits in the push target that are not in the pushed source.
 * Uses the local repository and the result of the last remote check of [GitBranchIncomingOutgoingManager].
 * Only a remote check and a fetch use the network.
 *
 * The suspend functions are for coroutines. The `Blocking` functions are for the callers on a background thread.
 */
@Service(Service.Level.PROJECT)
internal class GitPushDivergenceDetector(private val project: Project) {
  /**
   * Returns the divergence of the push of [source] to [target].
   * Returns `null` when the target has no commits that the source lacks, or when the source is behind the target.
   * When the remote has commits that are not fetched, the result is [PushDivergence.FetchRequired].
   *
   * @param checkRemote true to ask the remote for the position of the target without user interaction. This uses the network.
   * Otherwise, or when the remote can't be asked, the result of the last remote check of [GitBranchIncomingOutgoingManager] is used.
   */
  @RequiresBackgroundThread
  fun detectBlocking(repository: GitRepository, source: GitPushSource, target: GitPushTarget, checkRemote: Boolean): PushDivergence? {
    if (!isApplicable(source, target)) return null
    val trackedHash = repository.branches.getHash(target.branch) ?: return null
    val remoteHash = when (val remoteState = if (checkRemote) requestRemoteHash(repository, target) else RemoteState.Unknown) {
      is RemoteState.Present -> remoteState.hash
      RemoteState.Absent -> return null
      RemoteState.Unknown -> getUnfetchedRemoteHash(repository, source, target) ?: trackedHash
    }
    if (remoteHash != trackedHash && !commitExists(repository, remoteHash)) {
      // only a fetch can count the remote commits; a force push with lease expects the tracked position, so the remote rejects it
      return PushDivergence.FetchRequired(getLeaseTip(trackedHash))
    }

    val [outgoing, remoteOnly] = countLeftRight(repository, source.revision, remoteHash) ?: return null
    // when the source is behind the target, there is nothing to push, and a force push only drops the remote commits
    if (remoteOnly == 0 || outgoing == 0) return null

    val sourceBranch = source.branch
    val logBranchNames = if (source.isBranchRef && sourceBranch != null) listOf(sourceBranch.name, target.branch.nameForLocalOperations)
    else emptyList()
    return PushDivergence.Diverged(remoteOnly, getLeaseTip(remoteHash), logBranchNames)
  }

  private fun isApplicable(source: GitPushSource, target: GitPushTarget): Boolean =
    isEnabled() && !target.isNewBranchCreated && source !is GitPushSource.Tag

  /**
   * Returns the position that a force push with lease expects, or `null` when a force push doesn't check the position.
   */
  private fun getLeaseTip(remoteTip: Hash): Hash? =
    if (AdvancedSettings.getBoolean("git.use.push.force.with.lease")) remoteTip else null

  /**
   * Checks the default push of each repository before a push without the push dialog.
   * Fetches the target branch when the remote has commits that are not fetched, so that the push dialog can count them.
   *
   * @return true when a push target has commits that are not in the pushed source, so the user must see the push dialog
   */
  suspend fun hasDivergedDefaultPush(repositories: Collection<GitRepository>): Boolean = runGitTask {
    val pushSupport = DvcsUtil.getPushSupport(GitVcs.getInstance(project)) as? GitPushSupport ?: return@runGitTask false
    var diverged = false
    for (repository in repositories) {
      repository.update()
      val source = pushSupport.getSource(repository) ?: continue
      val target = pushSupport.getDefaultTarget(repository, source) ?: continue
      val divergence = detectBlocking(repository, source, target, checkRemote = true) ?: continue
      if (divergence is PushDivergence.FetchRequired && fetchTargetBlocking(repository, target) == null) {
        detectBlocking(repository, source, target, checkRemote = false) ?: continue
      }
      LOG.debug("The push of ${source.revision} to ${target.branch.fullName} has diverged from the remote")
      diverged = true
    }
    diverged
  }

  /**
   * Fetches the target branch of a push, so that [detectBlocking] can count its remote commits.
   *
   * @return the error to show, or `null` if the fetch succeeded
   */
  suspend fun fetchTarget(repository: GitRepository, target: GitPushTarget): VcsError? = runGitTask { fetchTargetBlocking(repository, target) }

  /**
   * The blocking variant of [fetchTarget].
   */
  @RequiresBackgroundThread
  fun fetchTargetBlocking(repository: GitRepository, target: GitPushTarget): VcsError? {
    val branch = target.branch
    val refspec = "+${GitBranch.REFS_HEADS_PREFIX}${branch.nameForRemoteOperations}:${branch.fullName}"
    try {
      GitFetchSupport.fetchSupport(project).fetch(repository, branch.remote, refspec).throwExceptionIfFailed()
      return null
    }
    catch (e: VcsException) {
      return VcsError(GitUtil.cleanupErrorPrefixes(e.message))
    }
    finally {
      repository.update()
    }
  }

  private sealed interface RemoteState {
    data class Present(val hash: Hash) : RemoteState
    data object Absent : RemoteState
    data object Unknown : RemoteState
  }

  /**
   * Returns the position of the target on the remote, or [RemoteState.Unknown] if the remote can't be asked without user interaction.
   */
  private fun requestRemoteHash(repository: GitRepository, target: GitPushTarget): RemoteState {
    val branch = target.branch
    val hashes = GitBranchIncomingOutgoingManager.getInstance(project)
                   .requestRemoteBranchHashes(repository, branch.remote, listOf(branch.nameForRemoteOperations)) ?: return RemoteState.Unknown
    val name = branch.nameForRemoteOperations
    val hash = hashes[if (name.startsWith("refs/")) name else GitBranch.REFS_HEADS_PREFIX + name]
    return if (hash != null) RemoteState.Present(hash) else RemoteState.Absent
  }

  private fun getUnfetchedRemoteHash(repository: GitRepository, source: GitPushSource, target: GitPushTarget): Hash? {
    val localBranch = source.branch ?: return null
    val trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, localBranch) ?: return null
    if (trackInfo.remoteBranch != target.branch) return null
    return GitBranchIncomingOutgoingManager.getInstance(project).getUnfetchedRemoteHash(repository, localBranch)
  }

  private fun commitExists(repository: GitRepository, hash: Hash): Boolean {
    return run(repository, GitCommand.CAT_FILE, "-e", "${hash.asString()}^{commit}").success()
  }

  /**
   * Returns the number of the commits that only [source] has and the number of the commits that only [remoteTip] has,
   * or `null` if git failed.
   */
  private fun countLeftRight(repository: GitRepository, source: String, remoteTip: Hash): Pair<Int, Int>? {
    val range = "$source...${remoteTip.asString()}"
    val result = run(repository, GitCommand.REV_LIST, "--left-right", "--count", range)
    val counts = if (result.success()) result.outputAsJoinedString.trim().split('\t').mapNotNull { it.toIntOrNull() } else emptyList()
    if (counts.size != 2) {
      LOG.warn("Couldn't count the commits in $range: ${result.errorOutputAsJoinedString}")
      return null
    }
    return counts[0] to counts[1]
  }

  private fun run(repository: GitRepository, command: GitCommand, vararg parameters: String): GitCommandResult {
    val handler = GitLineHandler(project, repository.root, command).apply {
      setSilent(true)
      addParameters(*parameters)
    }
    return Git.getInstance().runCommand(handler)
  }

  /**
   * Runs blocking git commands on the IO dispatcher. The cancellation of the coroutine cancels the commands.
   */
  private suspend fun <T> runGitTask(task: () -> T): T = withContext(Dispatchers.IO) { coroutineToIndicator { task() } }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GitPushDivergenceDetector = project.service()

    /**
     * Returns true when the registry turns on the check of the remote commits before a push.
     * With the check off, the push, the push dialog and the push notification behave as before the check.
     */
    @JvmStatic
    fun isEnabled(): Boolean = Registry.`is`("git.push.divergence.check", true)
  }
}
