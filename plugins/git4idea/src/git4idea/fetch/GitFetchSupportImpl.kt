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

import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.dvcs.MultiMessage
import com.intellij.dvcs.MultiRootMessage
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION
import git4idea.GitUtil.*
import git4idea.commands.Git
import git4idea.repo.GitRemote
import git4idea.repo.GitRemote.ORIGIN
import git4idea.repo.GitRepository
import java.util.regex.Pattern

private val LOG = logger<GitFetchSupportImpl>()
private val PRUNE_PATTERN = Pattern.compile("\\s*x\\s*\\[deleted\\].*->\\s*(\\S*)") // x [deleted]  (none) -> origin/branch

internal class GitFetchSupportImpl(val project: Project) : GitFetchSupport {

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

  override fun fetch(repositories: Collection<GitRepository>): GitFetchResult {
    val results = mutableMapOf<GitRepository, RepoResult>()
    for (repository in repositories) {
      val remote = getDefaultRemoteToFetch(repository)
      if (remote != null) {
        val repoResult = withIndicator(repository) {
          doFetch(repository, listOf(remote))
        }
        results[repository] = repoResult
      }
      else LOG.info("No remote to fetch found in $repository")
    }
    return resultOf(results)
  }

  override fun fetch(repository: GitRepository, remote: GitRemote): GitFetchResult {
    return withIndicator(repository) { fetch(repository, listOf(remote)) }
  }

  override fun fetch(repository: GitRepository, remotes: List<GitRemote>): GitFetchResult {
    return withIndicator(repository) { resultOf(mapOf(Pair(repository, doFetch(repository, remotes)))) }
  }

  private fun <T> withIndicator(repository: GitRepository, operation: () -> T): T {
    return withIndicator(getProgressTitle(repository), operation)
  }

  private fun <T> withIndicator(title: String, operation: () -> T): T {
    val indicator = ProgressManager.getInstance().progressIndicator
    val prevText = indicator?.text
    indicator?.text = title
    try {
      return operation()
    } finally {
      indicator?.text = prevText
    }
  }

  private fun getProgressTitle(repository: GitRepository): String {
    return "Fetching ${if (justOneGitRepository(project)) "" else getShortRepositoryName(repository)}"
  }

  private fun doFetch(repository: GitRepository, remotes: List<GitRemote>): RepoResult {
    val results = mutableMapOf<GitRemote, SingleRemoteResult>()
    for (remote in remotes) {
      results[remote] = doFetch(repository, remote)
    }
    return RepoResult(results)
  }

  private fun doFetch(repository: GitRepository, remote: GitRemote): SingleRemoteResult {
    val result = Git.getInstance().fetch(repository, remote, emptyList())
    val pruned = result.output.mapNotNull { getPrunedRef(it) }
    val error = if (result.success()) null else result.errorOutputAsJoinedString
    return SingleRemoteResult(error, pruned)
  }

  private fun getPrunedRef(line: String): String? {
    val matcher = PRUNE_PATTERN.matcher(line)
    return if (matcher.matches()) matcher.group(1) else null
  }

  private fun resultOf(results: Map<GitRepository, RepoResult>) = FetchResultImpl(project, results)

  private class RepoResult(val results: Map<GitRemote, SingleRemoteResult>) {
    /*
       For simplicity, remote and repository results are merged separately.
       It means that they are not merged, if two repositories have two remotes,
       and then fetch succeeds for the first remote in both repos, and fails for the second remote in both repos.
       Such cases are rare, and can be handled when actual problem is reported.
     */

    fun totallySuccessful() = results.values.all { it.success() }

    fun error(): String? {
      val errorMessage = multiRemoteMessage()
      for ((remote, result) in results) {
        if (result.error != null) errorMessage.append(remote, result.error)
      }
      return errorMessage.asString()
    }

    fun prunedRefs(): String {
      val prunedRefs = multiRemoteMessage()
      for ((remote, result) in results) {
        if (result.prunedRefs.isNotEmpty()) prunedRefs.append(remote, result.prunedRefs.joinToString("\n"))
      }
      return prunedRefs.asString()
    }

    private fun multiRemoteMessage() = MultiMessage(results.keys, GitRemote::getName, GitRemote::getName)
  }

  private class SingleRemoteResult(val error: String?, val prunedRefs: List<String>) {
    fun success() = error == null
  }

  private class FetchResultImpl(val project: Project,
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
      val prunedRefs = MultiRootMessage(project, roots, true)

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
      VcsNotifier.getInstance(project).notify(notification)
    }

    private fun prefixWithBr(text: String): String = if (text.isNotEmpty()) "<br/>$text" else ""
  }
}
