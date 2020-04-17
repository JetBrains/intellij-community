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
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.DelayedTaskScheduler
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture
import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

class GHPRStateModelImpl(private val project: Project,
                         private val stateService: GHPRStateService,
                         private val dataProvider: GHPRDataProvider,
                         override val details: GHPullRequestShort,
                         disposable: Disposable) : GHPRStateModel {

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
    dataProvider.addRequestsChangesListener(disposable, object : GHPRDataProvider.RequestsChangedListener {
      override fun mergeabilityStateRequestChanged() {
        loadMergeabilityState()
      }
    })
    loadMergeabilityState()
  }

  private fun loadMergeabilityState() {
    dataProvider.mergeabilityStateRequest.handleOnEdt { result: GHPRMergeabilityState?, error: Throwable? ->
      mergeabilityState = result
      mergeabilityLoadingError = error
      mergeabilityEventDispatcher.multicaster.eventOccurred()

      if (error == null && result?.hasConflicts == null) {
        mergeabilityPoller.start()
      }
      else mergeabilityPoller.stop()
    }
  }

  override fun reloadMergeabilityState() {
    dataProvider.reloadMergeabilityState()
  }

  override fun submitCloseTask() = submitTask {
    stateService.close(EmptyProgressIndicator(), details)
  }

  override fun submitReopenTask() = submitTask {
    stateService.reopen(EmptyProgressIndicator(), details)
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

    stateService.merge(EmptyProgressIndicator(), mergeability, dialog.message, mergeability.headRefOid)
  }

  override fun submitRebaseMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    stateService.rebaseMerge(EmptyProgressIndicator(), mergeability, mergeability.headRefOid)
  }

  override fun submitSquashMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    dataProvider.apiCommitsRequest.successOnEdt { commits ->
      val body = "* " + StringUtil.join(commits, { it.messageHeadline }, "\n\n* ")
      val dialog = GithubMergeCommitMessageDialog(project,
                                                  GithubBundle.message("pull.request.merge.message.dialog.title"),
                                                  GithubBundle.message("pull.request.merge.pull.request", mergeability.number),
                                                  body)
      if (!dialog.showAndGet()) {
        throw ProcessCanceledException()
      }
      dialog.message
    }.thenCompose { message ->
      stateService.squashMerge(EmptyProgressIndicator(), mergeability, message, mergeability.headRefOid)
    }
  }

  private fun submitTask(request: () -> CompletableFuture<*>?) {
    if (isBusy) return
    isBusy = true
    actionError = null

    val task = request()?.handleOnEdt { _, error ->
      actionError = error
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

  companion object {
    fun <T> observableField(initialValue: T, dispatcher: EventDispatcher<SimpleEventListener>): ObservableProperty<T> {
      return object : ObservableProperty<T>(initialValue) {
        override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = dispatcher.multicaster.eventOccurred()
      }
    }
  }
}