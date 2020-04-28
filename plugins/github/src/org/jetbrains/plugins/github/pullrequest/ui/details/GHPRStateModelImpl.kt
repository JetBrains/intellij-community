// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.DelayedTaskScheduler
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture

class GHPRStateModelImpl(private val project: Project,
                         private val dataProvider: GHPRDataProvider,
                         override val details: GHPullRequestShort,
                         disposable: Disposable) : GHPRStateModel {

  private val stateDataProvider = dataProvider.stateData

  private val mergeabilityEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  private val busyEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  private val actionErrorEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  private val mergeabilityPoller = DelayedTaskScheduler(3, disposable) {
    reloadMergeabilityState()
  }

  override var mergeabilityState: GHPRMergeabilityState? = null
    private set
  override var mergeabilityLoadingError: Throwable? = null
    private set

  override var isBusy: Boolean by observableField(false, busyEventDispatcher)
  override var actionError: Throwable? by observableField(null, actionErrorEventDispatcher)

  init {
    stateDataProvider.loadMergeabilityState(disposable) {
      it.handleOnEdt { result: GHPRMergeabilityState?, error: Throwable? ->
        mergeabilityState = result
        mergeabilityLoadingError = error
        mergeabilityEventDispatcher.multicaster.eventOccurred()

        if (error == null && result?.hasConflicts == null) {
          mergeabilityPoller.start()
        }
        else mergeabilityPoller.stop()
      }
    }
  }

  override fun reloadMergeabilityState() {
    stateDataProvider.reloadMergeabilityState()
  }

  override fun submitCloseTask() = submitTask {
    stateDataProvider.close(EmptyProgressIndicator())
  }

  override fun submitReopenTask() = submitTask {
    stateDataProvider.reopen(EmptyProgressIndicator())
  }

  override fun submitMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    val dialog = GithubMergeCommitMessageDialog(project,
                                                GithubBundle.message("pull.request.merge.message.dialog.title"),
                                                GithubBundle.message("pull.request.merge.pull.request", details.number),
                                                details.title)
    if (!dialog.showAndGet()) {
      return@submitTask null
    }

    stateDataProvider.merge(EmptyProgressIndicator(), dialog.message, mergeability.headRefOid)
  }

  override fun submitRebaseMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    stateDataProvider.rebaseMerge(EmptyProgressIndicator(), mergeability.headRefOid)
  }

  override fun submitSquashMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    dataProvider.changesData.loadCommitsFromApi().successOnEdt { commits ->
      val body = "* " + StringUtil.join(commits, { it.messageHeadline }, "\n\n* ")
      val dialog = GithubMergeCommitMessageDialog(project,
                                                  GithubBundle.message("pull.request.merge.message.dialog.title"),
                                                  GithubBundle.message("pull.request.merge.pull.request", details.number),
                                                  body)
      if (!dialog.showAndGet()) {
        throw ProcessCanceledException()
      }
      dialog.message
    }.thenCompose { message ->
      stateDataProvider.squashMerge(EmptyProgressIndicator(), message, mergeability.headRefOid)
    }
  }

  private fun submitTask(request: () -> CompletableFuture<*>?) {
    if (isBusy) return
    isBusy = true
    actionError = null

    val task = request()?.handleOnEdt { _, error ->
      actionError = error?.takeIf { !GithubAsyncUtil.isCancellation(it) }
      isBusy = false
    }
    if (task == null) {
      isBusy = false
      return
    }
  }

  override fun addAndInvokeMergeabilityStateLoadingResultListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(mergeabilityEventDispatcher, listener)

  override fun addAndInvokeBusyStateChangedListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(busyEventDispatcher, listener)

  override fun addAndInvokeActionErrorChangedListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(actionErrorEventDispatcher, listener)
}