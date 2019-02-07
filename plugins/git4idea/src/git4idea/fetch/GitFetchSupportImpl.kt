/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.fetch

import com.intellij.dvcs.MultiMessage
import com.intellij.dvcs.MultiRootMessage
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.GitUtil.findRemoteByName
import git4idea.GitUtil.mention
import git4idea.commands.Git
import git4idea.commands.GitAuthenticationGate
import git4idea.commands.GitImpl
import git4idea.commands.GitRestrictingAuthenticationGate
import git4idea.config.GitConfigUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRemote.ORIGIN
import git4idea.repo.GitRepository
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.regex.Pattern

private val LOG = logger<GitFetchSupportImpl>()
private val PRUNE_PATTERN = Pattern.compile("\\s*x\\s*\\[deleted\\].*->\\s*(\\S*)") // x [deleted]  (none) -> origin/branch
private const val MAX_SSH_CONNECTIONS = 10 // by default SSH server has a limit of 10 multiplexed ssh connection

internal class GitFetchSupportImpl(git: Git,
                                   private val project: Project,
                                   private val progressManager : ProgressManager,
                                   private val vcsNotifier : VcsNotifier) : GitFetchSupport {

  private val git = git as GitImpl

  override fun getDefaultRemoteToFetch(repository: GitRepository): GitRemote? {
    val remotes = repository.remotes
    return when {
      remotes.isEmpty() -> null
      remotes.size == 1 -> remotes.first()
      else -> {
        // this emulates behavior of the native `git fetch`:
        // if current branch doesn't give a hint, then return "origin"; if there is no "origin", don't guess and fail
        repository.currentBranch?.findTrackedBranch(repository)?.remote ?: findRemoteByName(repository, ORIGIN)
      }
    }
  }

  override fun fetchDefaultRemote(repositories: Collection<GitRepository>): GitFetchResult {
    val remotesToFetch = mutableListOf<Pair<GitRepository, GitRemote>>()
    for (repository in repositories) {
      val remote = getDefaultRemoteToFetch(repository)
      if (remote != null) remotesToFetch.add(repository to remote)
      else LOG.info("No remote to fetch found in $repository")
    }
    return fetch(remotesToFetch)
  }

  override fun fetchAllRemotes(repositories: Collection<GitRepository>): GitFetchResult {
    val remotesToFetch = mutableListOf<Pair<GitRepository, GitRemote>>()
    for (repository in repositories) {
      if (repository.remotes.isEmpty()) {
        LOG.info("No remote to fetch found in $repository")
      }
      else {
        for (remote in repository.remotes) {
          remotesToFetch.add(repository to remote)
        }
      }
    }
    return fetch(remotesToFetch)
  }

  override fun fetch(repository: GitRepository, remote: GitRemote): GitFetchResult {
    return fetch(listOf(repository to remote))
  }

  private fun fetch(remotes: List<Pair<GitRepository, GitRemote>>): GitFetchResult {
    return withIndicator {
      val tasks = fetchInParallel(remotes)
      val results = waitForFetchTasks(tasks)

      val mergedResults = mutableMapOf<GitRepository, RepoResult>()
      for (result in results) {
        val res = mergedResults[result.repository]
        mergedResults[result.repository] = mergeRepoResults(res, result)
      }
      FetchResultImpl(project, vcsNotifier, mergedResults)
    }
  }

  private fun mergeRepoResults(firstResult: RepoResult?, secondResult: SingleRemoteResult): RepoResult {
    if (firstResult == null) {
      return RepoResult(mapOf(secondResult.remote to secondResult))
    }
    else {
      return RepoResult(firstResult.results + (secondResult.remote to secondResult))
    }
  }

  private fun fetchInParallel(remotes: List<Pair<GitRepository, GitRemote>>): List<FetchTask> {
    val tasks = mutableListOf<FetchTask>()
    val maxThreads = getMaxThreads(remotes.mapTo(HashSet()) {it.first}, remotes.size)
    LOG.debug("Fetching $remotes using $maxThreads threads")
    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("GitFetch pool", maxThreads)
    val commonIndicator = progressManager.progressIndicator ?: EmptyProgressIndicator()
    val authenticationGate = GitRestrictingAuthenticationGate()
    for ((repository, remote) in remotes) {
      LOG.debug("Fetching $remote in $repository")
      val future: Future<SingleRemoteResult> = executor.submit<SingleRemoteResult> {
        commonIndicator.checkCanceled()
        lateinit var result: SingleRemoteResult
        ProgressManager.getInstance().executeProcessUnderProgress({
          commonIndicator.checkCanceled()
          result = doFetch(repository, remote, authenticationGate)
        }, commonIndicator)
        result
      }
      tasks.add(FetchTask(repository, remote, future))
    }
    return tasks
  }

  private fun getMaxThreads(repositories: Collection<GitRepository>, numberOfRemotes: Int): Int {
    val config = Registry.intValue("git.parallel.fetch.threads")
    val maxThreads = when {
      config > 0 -> config
      config == -1 -> Runtime.getRuntime().availableProcessors()
      config == -2 -> numberOfRemotes
      config == -3 -> Math.min(numberOfRemotes, Runtime.getRuntime().availableProcessors() * 2)
      else -> 1
    }

    if (isStoreCredentialsHelperUsed(repositories)) {
      return 1
    }

    return Math.min(maxThreads, MAX_SSH_CONNECTIONS)
  }

  private fun isStoreCredentialsHelperUsed(repositories: Collection<GitRepository>): Boolean {
    return repositories.any { GitConfigUtil.getValue(project, it.root, "credential.helper").equals("store", ignoreCase = true) }
  }

  private fun waitForFetchTasks(tasks: List<FetchTask>): List<SingleRemoteResult> {
    val results = mutableListOf<SingleRemoteResult>()
    for (task in tasks) {
      try {
        results.add(task.future.get())
      }
      catch (e: CancellationException) {
        throw ProcessCanceledException(e)
      }
      catch (e: InterruptedException) {
        throw ProcessCanceledException(e)
      }
      catch (e: ExecutionException) {
        if (e.cause is ProcessCanceledException) throw e.cause as ProcessCanceledException
        results.add(SingleRemoteResult(task.repository, task.remote, e.cause?.message ?: "Error", emptyList()))
        LOG.error(e)
      }
    }
    return results
  }

  private fun <T> withIndicator(operation: () -> T): T {
    val indicator = progressManager.progressIndicator
    val prevText = indicator?.text
    indicator?.text = "Fetching"
    try {
      return operation()
    } finally {
      indicator?.text = prevText
    }
  }

  private fun doFetch(repository: GitRepository, remote: GitRemote, authenticationGate: GitAuthenticationGate? = null): SingleRemoteResult {
    val result = git.fetch(repository, remote, emptyList(), authenticationGate)
    val pruned = result.output.mapNotNull { getPrunedRef(it) }
    if (result.success()) {
      repository.update()
    }
    val error = if (result.success()) null else result.errorOutputAsJoinedString
    return SingleRemoteResult(repository, remote, error, pruned)
  }

  private fun getPrunedRef(line: String): String? {
    val matcher = PRUNE_PATTERN.matcher(line)
    return if (matcher.matches()) matcher.group(1) else null
  }

  private class FetchTask(val repository: GitRepository, val remote: GitRemote, val future: Future<SingleRemoteResult>)

  private class RepoResult(val results: Map<GitRemote, SingleRemoteResult>) {

    fun totallySuccessful() = results.values.all { it.success() }

    fun error(): String? {
      val errorMessage = multiRemoteMessage(true)
      for ((remote, result) in results) {
        if (result.error != null) errorMessage.append(remote, result.error)
      }
      return errorMessage.asString()
    }

    fun prunedRefs(): String {
      val prunedRefs = multiRemoteMessage(false)
      for ((remote, result) in results) {
        if (result.prunedRefs.isNotEmpty()) prunedRefs.append(remote, result.prunedRefs.joinToString("\n"))
      }
      return prunedRefs.asString()
    }

    /*
       For simplicity, remote and repository results are merged separately.
       It means that they are not merged, if two repositories have two remotes,
       and then fetch succeeds for the first remote in both repos, and fails for the second remote in both repos.
       Such cases are rare, and can be handled when actual problem is reported.
     */
    private fun multiRemoteMessage(remoteInPrefix: Boolean) =
      MultiMessage(results.keys, GitRemote::getName, GitRemote::getName, remoteInPrefix)
  }

  private class SingleRemoteResult(val repository: GitRepository, val remote: GitRemote, val error: String?, val prunedRefs: List<String>) {
    fun success() = error == null
  }

  private class FetchResultImpl(val project: Project,
                                val vcsNotifier : VcsNotifier,
                                val results: Map<GitRepository, RepoResult>) : GitFetchResult {

    override fun showNotification() {
      doShowNotification()
    }

    override fun showNotificationIfFailed(): Boolean {
      return showNotificationIfFailed("Fetch Failed")
    }

    override fun showNotificationIfFailed(title: String): Boolean {
      val failure = results.values.any { !it.totallySuccessful() }
      if (failure) doShowNotification(title)
      return !failure
    }

    private fun doShowNotification(failureTitle: String = "Fetch Failed") {
      val roots = results.keys.map { it.root }
      val errorMessage = MultiRootMessage(project, roots, true)
      val prunedRefs = MultiRootMessage(project, roots)

      val failed = results.filterValues { !it.totallySuccessful() }

      for ((repo, result) in failed) {
        if (result.error() != null) errorMessage.append(repo.root, result.error()!!)
      }
      for ((repo, result) in results) {
        prunedRefs.append(repo.root, result.prunedRefs())
      }

      val type = if (failed.isEmpty()) NotificationType.INFORMATION else NotificationType.ERROR
      val mentionFailedRepos = if (failed.size == roots.size) "" else mention(failed.keys)
      val title = if (failed.isEmpty()) "<b>Fetch Successful</b>" else "<b>$failureTitle</b>$mentionFailedRepos"
      val message = title + prefixWithBr(errorMessage.asString()) + prefixWithBr(prunedRefs.asString())
      val notification = STANDARD_NOTIFICATION.createNotification("", message, type, null)
      vcsNotifier.notify(notification)
    }

    private fun prefixWithBr(text: String): String = if (text.isNotEmpty()) "<br/>$text" else ""
  }
}
