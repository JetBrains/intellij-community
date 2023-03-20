// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import git4idea.remote.hosting.GitHostingUrlUtil.match
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

/**
 * Various utility methods for the GutHub plugin.
 */
object GithubUtil {

  @JvmField
  val LOG: Logger = Logger.getInstance("github")

  @NlsSafe
  const val SERVICE_DISPLAY_NAME: String = "GitHub"

  @NlsSafe
  const val ENTERPRISE_SERVICE_DISPLAY_NAME: String = "GitHub Enterprise"
  const val GIT_AUTH_PASSWORD_SUBSTITUTE: String = "x-oauth-basic"

  @JvmStatic
  fun addCancellationListener(run: () -> Unit): ScheduledFuture<*> {
    return JobScheduler.getScheduler().scheduleWithFixedDelay(run, 1000, 300, TimeUnit.MILLISECONDS)
  }

  private fun addCancellationListener(indicator: ProgressIndicator, thread: Thread): ScheduledFuture<*> {
    return addCancellationListener { if (indicator.isCanceled) thread.interrupt() }
  }

  @Throws(IOException::class)
  @JvmStatic
  fun <T> runInterruptable(indicator: ProgressIndicator,
                           task: ThrowableComputable<T, IOException>): T {
    var future: ScheduledFuture<*>? = null
    try {
      val thread = Thread.currentThread()
      future = addCancellationListener(indicator, thread)

      return task.compute()
    }
    finally {
      future?.cancel(true)
      Thread.interrupted()
    }
  }

  @NlsSafe
  @JvmStatic
  fun getErrorTextFromException(e: Throwable): String {
    return if (e is UnknownHostException) {
      "Unknown host: " + e.message
    }
    else StringUtil.notNullize(e.message, "Unknown error")
  }

  /**
   * Splits full commit message into subject and description in GitHub style:
   * First line becomes subject, everything after first line becomes description
   * Also supports empty line that separates subject and description
   *
   * @param commitMessage full commit message
   * @return couple of subject and description based on full commit message
   */
  @JvmStatic
  fun getGithubLikeFormattedDescriptionMessage(commitMessage: String?): Couple<String> {
    //Trim original
    val message = commitMessage?.trim { it <= ' ' } ?: ""
    if (message.isEmpty()) {
      return Couple.of("", "")
    }
    val firstLineEnd = message.indexOf("\n")
    val subject: String
    val description: String
    if (firstLineEnd > -1) {
      //Subject is always first line
      subject = message.substring(0, firstLineEnd).trim { it <= ' ' }
      //Description is all text after first line, we also trim it to remove empty lines on start of description
      description = message.substring(firstLineEnd + 1).trim { it <= ' ' }
    }
    else {
      //If we don't have any line separators and cannot detect description,
      //we just assume that it is one-line commit and use full message as subject with empty description
      subject = message
      description = ""
    }

    return Couple.of(subject, description)
  }

  object Delegates {

    fun <T> observableField(initialValue: T, dispatcher: EventDispatcher<SimpleEventListener>): ObservableProperty<T> {
      return object : ObservableProperty<T>(initialValue) {
        override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = dispatcher.multicaster.eventOccurred()
      }
    }
  }

  @JvmStatic
  @Deprecated("{@link GithubGitHelper}", ReplaceWith("GithubGitHelper.findGitRepository(project, file)",
                                                     "org.jetbrains.plugins.github.util.GithubGitHelper"))
  @ApiStatus.ScheduledForRemoval
  fun getGitRepository(project: Project, file: VirtualFile?): GitRepository? {
    return GithubGitHelper.findGitRepository(project, file)
  }

  @JvmStatic
  @Deprecated("{@link GithubGitHelper}")
  private fun findGithubRemoteUrl(repository: GitRepository): String? {
    val remote = findGithubRemote(repository) ?: return null
    return remote.getSecond()
  }

  @JvmStatic
  @Deprecated("{@link org.jetbrains.plugins.github.api.GithubServerPath}, {@link GithubGitHelper}")
  private fun findGithubRemote(repository: GitRepository): Pair<GitRemote, String>? {
    val server = GithubAuthenticationManager.getInstance().getSingleOrDefaultAccount(repository.project)?.server ?: return null

    var githubRemote: Pair<GitRemote, String>? = null
    for (gitRemote in repository.remotes) {
      for (remoteUrl in gitRemote.urls) {
        if (match(server.toURI(), remoteUrl)) {
          val remoteName = gitRemote.name
          if ("github" == remoteName || "origin" == remoteName) {
            return Pair.create(gitRemote, remoteUrl)
          }
          if (githubRemote == null) {
            githubRemote = Pair.create(gitRemote, remoteUrl)
          }
          break
        }
      }
    }
    return githubRemote
  }

  @JvmStatic
  @Deprecated("{@link org.jetbrains.plugins.github.api.GithubServerPath}")
  @ApiStatus.ScheduledForRemoval
  fun isRepositoryOnGitHub(repository: GitRepository): Boolean {
    return findGithubRemoteUrl(repository) != null
  }
}
