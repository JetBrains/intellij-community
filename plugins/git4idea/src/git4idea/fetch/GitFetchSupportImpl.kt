// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import com.intellij.dvcs.MultiMessage
import com.intellij.dvcs.MultiRootMessage
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil.findRemoteByName
import git4idea.GitUtil.mention
import git4idea.commands.Git
import git4idea.commands.GitAuthenticationGate
import git4idea.commands.GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS
import git4idea.commands.GitImpl
import git4idea.commands.GitRestrictingAuthenticationGate
import git4idea.config.GitConfigUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRemote.ORIGIN
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

private val LOG = logger<GitFetchSupportImpl>()
private val PRUNE_PATTERN = Pattern.compile("\\s*x\\s*\\[deleted\\].*->\\s*(\\S*)") // x [deleted]  (none) -> origin/branch
private const val MAX_SSH_CONNECTIONS = 10 // by default SSH server has a limit of 10 multiplexed ssh connection

internal class GitFetchSupportImpl(private val project: Project) : GitFetchSupport {
  private val git get() = Git.getInstance() as GitImpl
  private val progressManager get() = ProgressManager.getInstance()

  private val fetchQueue = GitRemoteOperationQueueImpl()
  private val fetchRequestCounter = AtomicInteger()

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
    val remotesToFetch = mutableListOf<RemoteRefCoordinates>()
    for (repository in repositories) {
      val remote = getDefaultRemoteToFetch(repository)
      if (remote != null) remotesToFetch.add(RemoteRefCoordinates(repository, remote))
      else LOG.info("No remote to fetch found in $repository")
    }
    return fetch(remotesToFetch)
  }

  override fun fetchAllRemotes(repositories: Collection<GitRepository>): GitFetchResult {
    val remotesToFetch = mutableListOf<RemoteRefCoordinates>()
    for (repository in repositories) {
      if (repository.remotes.isEmpty()) {
        LOG.info("No remote to fetch found in $repository")
      }
      else {
        for (remote in repository.remotes) {
          remotesToFetch.add(RemoteRefCoordinates(repository, remote))
        }
      }
    }
    return fetch(remotesToFetch)
  }

  override fun fetch(repository: GitRepository, remote: GitRemote): GitFetchResult {
    return fetch(listOf(RemoteRefCoordinates(repository, remote)))
  }

  override fun fetch(repository: GitRepository, remote: GitRemote, refspec: @NonNls String): GitFetchResult {
    return fetch(listOf(RemoteRefCoordinates(repository, remote, refspec)))
  }

  private fun fetch(arguments: List<RemoteRefCoordinates>): GitFetchResult {
    try {
      fetchRequestCounter.incrementAndGet()
      return withIndicator {
        val activity = VcsStatisticsCollector.FETCH_ACTIVITY.started(project)

        val tasks = fetchInParallel(arguments)
        val results = waitForFetchTasks(tasks)

        val mergedResults = mutableMapOf<GitRepository, RepoResult>()
        val succeedResults = mutableListOf<SingleRemoteResult>()
        for (result in results) {
          val res = mergedResults[result.repository]
          mergedResults[result.repository] = mergeRepoResults(res, result)
          if (result.success()) succeedResults.add(result)
        }
        val successFetchesMap = succeedResults.groupBy({ it.repository }, { it.remote })
        if (successFetchesMap.isNotEmpty()) {
          GitFetchHandler.afterSuccessfulFetch(project, successFetchesMap, progressManager.progressIndicator ?: EmptyProgressIndicator())
        }
        activity.finished()
        FetchResultImpl(project, VcsNotifier.getInstance(project), mergedResults)
      }
    }
    finally {
      fetchRequestCounter.decrementAndGet()
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

  override fun isFetchRunning() = fetchRequestCounter.get() > 0

  private fun fetchInParallel(remotes: List<RemoteRefCoordinates>): List<FetchTask> {
    val tasks = mutableListOf<FetchTask>()
    val maxThreads = getMaxThreads(remotes.mapTo(HashSet()) { it.repository }, remotes.size)
    LOG.debug("Fetching $remotes using $maxThreads threads")
    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("GitFetch pool", maxThreads)
    val commonIndicator = progressManager.progressIndicator ?: EmptyProgressIndicator()
    val authenticationGate = GitRestrictingAuthenticationGate()
    for ((repository, remote, refspec) in remotes) {
      LOG.debug("Fetching $remote in $repository")
      val future: Future<SingleRemoteResult> = executor.submit<SingleRemoteResult> {
        commonIndicator.checkCanceled()
        lateinit var result: SingleRemoteResult

        ProgressManager.getInstance().executeProcessUnderProgress({
                                                                    commonIndicator.checkCanceled()
                                                                    result = fetchQueue.executeForRemote(repository, remote) {
                                                                      doFetch(repository, remote, refspec, authenticationGate)
                                                                    }
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
        results.add(SingleRemoteResult(task.repository, task.remote, e.cause?.message ?: GitBundle.message("error.dialog.title"), emptyList()))
        LOG.error(e)
      }
    }
    return results
  }

  private fun <T> withIndicator(operation: () -> T): T {
    val indicator = progressManager.progressIndicator
    val prevText = indicator?.text
    indicator?.text = GitBundle.message("git.fetch.progress")
    try {
      return operation()
    }
    finally {
      indicator?.text = prevText
    }
  }

  private fun doFetch(repository: GitRepository, remote: GitRemote, refspec: String?, authenticationGate: GitAuthenticationGate? = null)
    : SingleRemoteResult {

    val recurseSubmodules = "--recurse-submodules=no"
    val params = if (refspec == null) arrayOf(recurseSubmodules) else arrayOf(refspec, recurseSubmodules)
    val result = git.fetch(repository, remote, emptyList(), authenticationGate, *params)
    val pruned = result.output.mapNotNull { getPrunedRef(it) }
    if (result.success()) {
      BackgroundTaskUtil.syncPublisher(repository.project, GIT_AUTHENTICATION_SUCCESS).authenticationSucceeded(repository, remote)
      repository.update()
    }
    val error = if (result.success()) null else result.errorOutputAsJoinedString
    return SingleRemoteResult(repository, remote, error, pruned)
  }

  private fun getPrunedRef(line: String): String? {
    val matcher = PRUNE_PATTERN.matcher(line)
    return if (matcher.matches()) matcher.group(1) else null
  }

  private data class RemoteRefCoordinates(val repository: GitRepository, val remote: GitRemote, val refspec: String? = null)

  private class FetchTask(val repository: GitRepository, val remote: GitRemote, val future: Future<SingleRemoteResult>)

  private class RepoResult(val results: Map<GitRemote, SingleRemoteResult>) {

    fun totallySuccessful() = results.values.all { it.success() }

    fun error(): @Nls String {
      val errorMessage = multiRemoteMessage(true)
      for ((remote, result) in results) {
        if (result.error != null) errorMessage.append(remote, result.error)
      }
      return errorMessage.asString()
    }

    fun prunedRefs(): @NlsSafe String {
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

  private class SingleRemoteResult(val repository: GitRepository, val remote: GitRemote, val error: @Nls String?, val prunedRefs: List<String>) {
    fun success() = error == null
  }

  private class FetchResultImpl(val project: Project,
                                val vcsNotifier: VcsNotifier,
                                val results: Map<GitRepository, RepoResult>) : GitFetchResult {

    private val isFailed = results.values.any { !it.totallySuccessful() }

    override fun showNotification() {
      doShowNotification()
    }

    override fun showNotificationIfFailed(): Boolean {
      if (isFailed) doShowNotification(null)
      return !isFailed
    }

    override fun showNotificationIfFailed(title: @Nls String): Boolean {
      if (isFailed) doShowNotification(title)
      return !isFailed
    }

    private fun doShowNotification(failureTitle: @Nls String? = null) {
      val type = if (!isFailed) NotificationType.INFORMATION else NotificationType.ERROR
      val message = buildMessage(failureTitle)
      val notification = STANDARD_NOTIFICATION.createNotification(message, type)
      notification.setDisplayId(if (!isFailed) GitNotificationIdsHolder.FETCH_RESULT else GitNotificationIdsHolder.FETCH_RESULT_ERROR)
      vcsNotifier.notify(notification)
    }

    override fun throwExceptionIfFailed() {
      if (isFailed) throw VcsException(buildMessage(null))
    }

    private fun buildMessage(failureTitle: @Nls String?): @Nls String {
      val roots = results.keys.map { it.root }
      val errorMessage = MultiRootMessage(project, roots, true)
      val prunedRefs = MultiRootMessage(project, roots)

      val failed = results.filterValues { !it.totallySuccessful() }

      for ((repo, result) in failed) {
        errorMessage.append(repo.root, result.error())
      }
      for ((repo, result) in results) {
        prunedRefs.append(repo.root, result.prunedRefs())
      }

      val sb = HtmlBuilder()
      if (!isFailed) {
        sb.append(HtmlChunk.text(GitBundle.message("notification.title.fetch.success")).bold())
      }
      else {
        sb.append(HtmlChunk.text(failureTitle ?: GitBundle.message("notification.title.fetch.failure")).bold())
        if (failed.size != roots.size) {
          sb.append(mention(failed.keys))
        }
      }
      appendDetails(sb, errorMessage)
      appendDetails(sb, prunedRefs)
      return sb.toString()
    }

    private fun appendDetails(sb: HtmlBuilder, details: MultiRootMessage) {
      val text = details.asString()
      if (text.isNotEmpty()) {
        sb.br().append(text)
      }
    }
  }
}
