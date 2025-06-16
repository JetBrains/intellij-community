// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


internal class GHPRListETagUpdateChecker(
  parentCs: CoroutineScope,
  private val progressManager: ProgressManager,
  private val requestExecutor: GithubApiRequestExecutor,
  private val serverPath: GithubServerPath,
  private val repoPath: GHRepositoryPath,
) : GHPRListUpdatesChecker {
  companion object {
    private const val REFRESH_ENABLED_KEY = "github.pr.list.automatic.refresh.enabled"
    private const val REFRESH_SECONDS_KEY = "github.pr.list.automatic.refresh.seconds"
  }

  private val outdatedEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  override var outdated by observableField(false, outdatedEventDispatcher)
    private set

  private var scheduler: ScheduledFuture<*>? = null
  private var progressIndicator: ProgressIndicator? = null

  init {
    parentCs.launch(Dispatchers.Main.immediate) {
      scheduler?.cancel(true)
      progressIndicator?.cancel()
    }
  }

  @Volatile
  private var lastETag: String? = null
    set(value) {
      val current = field
      runInEdt { outdated = current != null && value != null && current != value }
      field = value
    }

  override fun start() {
    if (scheduler == null && Registry.`is`(REFRESH_ENABLED_KEY, true)) {
      val secondsBetweenRefreshes = Registry.get(REFRESH_SECONDS_KEY).asInteger().toLong()

      val indicator = NonReusableEmptyProgressIndicator()
      progressIndicator = indicator
      scheduler = JobScheduler.getScheduler().scheduleWithFixedDelay(
        {
          try {
            val etag = loadListETag(indicator)
            lastETag = etag
          }
          catch (_: Exception) {
            //ignore
          }
        }, secondsBetweenRefreshes, secondsBetweenRefreshes, TimeUnit.SECONDS)
    }
  }

  private fun loadListETag(indicator: ProgressIndicator): String? =
    progressManager.runProcess(Computable {
      requestExecutor.execute(indicator, GithubApiRequests.Repos.PullRequests.getListETag(serverPath, repoPath))
    }, indicator)

  override fun stop() {
    scheduler?.cancel(true)
    scheduler = null
    progressIndicator?.cancel()
    progressIndicator = NonReusableEmptyProgressIndicator()
    lastETag = null
  }

  override fun addOutdatedStateChangeListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(outdatedEventDispatcher, disposable, listener)
}