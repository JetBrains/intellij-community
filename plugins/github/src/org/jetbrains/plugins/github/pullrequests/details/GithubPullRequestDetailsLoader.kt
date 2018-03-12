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
package org.jetbrains.plugins.github.pullrequests.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerUtil
import git4idea.commands.GitLineHandler
import git4idea.config.GitVersionSpecialty
import git4idea.history.GitHistoryUtils
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiUtil
import org.jetbrains.plugins.github.api.GithubConnection
import org.jetbrains.plugins.github.api.data.GithubCommit
import org.jetbrains.plugins.github.api.data.GithubCommitComment
import org.jetbrains.plugins.github.api.data.GithubIssueComment
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.pullrequests.GithubToolWindow
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUrlUtil
import org.jetbrains.plugins.github.util.GithubUtil
import java.io.IOException
import java.util.*

open class GithubPullRequestDetailsLoader(private val toolWindow: GithubToolWindow, private val number: Long) : Disposable {
  private val indicator: ProgressIndicator = EmptyProgressIndicator()

  var pullRequest: GithubPullRequestDetailed? = null
    private set
  var commitComments: List<GithubCommitComment>? = null
    private set
  var issueComments: List<GithubIssueComment>? = null
    private set
  var commits: List<GithubCommitDetails>? = null
    private set

  private var requestAndCommitsLoading = true
  private var commitCommentsLoading = true
  private var issueCommentsLoading = true
  private var commitsChangesLoading = true
  private var remoteFetching = true

  private var shouldLoadCommitsAfterFetch = false


  @CalledInAwt
  fun load() {
    fetchRemote()
    loadRequestAndCommits()
    loadCommitComments()
    loadIssueComments()
  }

  override fun dispose() {
    indicator.cancel()
  }

  @CalledInAwt
  protected open fun onRemoteFetched() {
  }

  @CalledInAwt
  protected open fun onCommitCommentsLoaded() {
  }

  @CalledInAwt
  protected open fun onIssueCommentsLoaded() {
  }

  @CalledInAwt
  protected open fun onRequestAndCommitsLoaded() {
  }

  @CalledInAwt
  protected open fun onCommitsChangesLoaded(loadingComplete: Boolean) {
  }


  @CalledInAwt
  private fun fetchRemote() {
    loadInBackground(
        {
          doFetchRemote()
        },
        {
          remoteFetching = false
          onRemoteFetched()

          if (shouldLoadCommitsAfterFetch) {
            loadCommits(true)
          }
        }
    )
  }

  @CalledInAwt
  private fun loadRequestAndCommits() {
    loadInBackground(
        {
          val request = doLoad(GithubApiUtil::getPullRequest)
          val commits = doLoad(GithubApiUtil::getPullRequestCommits)
          Pair(request, commits)
        },
        {
          pullRequest = it?.first
          commits = it?.second?.map(::GithubCommitDetails)
          requestAndCommitsLoading = false
          onRequestAndCommitsLoaded()

          loadCommits(!remoteFetching)
        }
    )
  }

  @CalledInAwt
  private fun loadCommitComments() {
    loadInBackground(
        {
          doLoad(GithubApiUtil::getPullRequestComments)
        },
        {
          commitComments = it
          commitCommentsLoading = false
          onCommitCommentsLoaded()
        }
    )
  }

  @CalledInAwt
  private fun loadIssueComments() {
    loadInBackground(
        {
          doLoad(GithubApiUtil::getIssueComments)
        },
        {
          issueComments = it
          issueCommentsLoading = false
          onIssueCommentsLoaded()
        }
    )
  }

  @CalledInAwt
  private fun loadCommits(isFetched: Boolean) {
    val commits = this.commits
    if (commits == null) {
      commitsChangesLoading = false
      onCommitsChangesLoaded(true)
      return
    }

    loadInBackground(
        {
          var allCommitsLoaded = true
          val commitChanges = ArrayList<List<Change>?>(Collections.nCopies(commits.size, null))

          for (i in commits.indices.reversed()) {
            try {
              val commitDetails = commits[i]
              if (commitDetails.changes == null) {
                commitChanges[i] = doLoadCommitChanges(commitDetails.commit)
              }
            }
            catch (e: IOException) {
              allCommitsLoaded = false
              if (!isFetched) break
              GithubNotifications.showError(toolWindow.project, "Can't load pull request details", e)
            }
          }
          Pair(commitChanges, allCommitsLoaded)
        },
        {
          val commitChanges = it?.first
          val allCommitsLoaded = it?.second ?: false

          if (commitChanges != null) {
            commits.forEachIndexed { i, commitDetails ->
              commitDetails.changes = commitChanges[i] ?: commitDetails.changes
            }
          }

          val loadingComplete = isFetched || allCommitsLoaded
          if (!loadingComplete) {
            if (remoteFetching) {
              shouldLoadCommitsAfterFetch = true
            }
            else {
              loadCommits(true)
            }
          }

          if (loadingComplete) commitsChangesLoading = false
          onCommitsChangesLoaded(loadingComplete)
        }
    )
  }

  @CalledInAwt
  private fun <T> loadInBackground(task: () -> T, callback: (T?) -> Unit) {
    ApplicationManager.getApplication().executeOnPooledThread {
      var result: T? = null
      try {
        result = task()
      }
      catch (e: IOException) {
        GithubNotifications.showError(toolWindow.project, "Can't load pull request details", e)
      }
      finally {
        ApplicationManager.getApplication().invokeLater {
          callback(result)
        }
      }
    }
  }


  @Throws(IOException::class)
  private fun <T> doLoad(task: (GithubConnection, user: String, repository: String, number: Long) -> T): T {
    return GithubUtil.runTask(toolWindow.project, toolWindow.authDataHolder, indicator) { connection ->
      task(connection, toolWindow.fullPath.user, toolWindow.fullPath.repository, number)
    }
  }

  private fun doFetchRemote() {
    val remoteUrl = GithubUrlUtil.getCloneUrl(toolWindow.fullPath)
    val removeBranch = "pull/$number/head"

    val gitRepository = toolWindow.gitRepository

    val h = GitLineHandler(toolWindow.project, gitRepository.root, GitCommand.FETCH)
    h.setUrls(listOf(remoteUrl))
    h.addParameters(remoteUrl)
    h.addParameters(removeBranch)
    h.addProgressParameter()
    h.setSilent(false)
    h.setStdoutSuppressed(false)

    GitHandlerUtil.runInCurrentThread(h, null)
    gitRepository.update()
  }

  @Throws(IOException::class)
  private fun doLoadCommitChanges(commit: GithubCommit): List<Change> {
    val gitRepository = toolWindow.gitRepository
    val noWalk = if (GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(gitRepository.vcs.version)) "--no-walk=unsorted" else "--no-walk"

    try {
      val history = GitHistoryUtils.history(toolWindow.project, gitRepository.root, noWalk, commit.sha)
      if (history.size != 1) throw VcsException("More than one commit loaded for a single hash")
      return history.single().changes.toList()
    }
    catch (e: VcsException) {
      throw IOException(e)
    }
  }

  class GithubCommitDetails(val commit: GithubCommit) {
    var changes: List<Change>? = null
  }
}
