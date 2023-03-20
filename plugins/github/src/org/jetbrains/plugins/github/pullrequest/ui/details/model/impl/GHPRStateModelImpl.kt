// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.action.ReviewMergeCommitMessageDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRStateDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import org.jetbrains.plugins.github.util.DelayedTaskScheduler
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField
import java.util.concurrent.CompletableFuture

class GHPRStateModelImpl(private val project: Project,
                         private val stateData: GHPRStateDataProvider,
                         private val changesData: GHPRChangesDataProvider,
                         private val detailsModel: SingleValueModel<out GHPullRequestShort>,
                         disposable: Disposable) : GHPRStateModel {

  private val mergeabilityEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  private val busyEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)
  private val actionErrorEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  private val mergeabilityPoller = DelayedTaskScheduler(3, disposable) {
    reloadMergeabilityState()
  }

  private val details: GHPullRequestShort
    get() = detailsModel.value

  override val viewerDidAuthor = details.viewerDidAuthor
  override val isDraft: Boolean
    get() = details.isDraft

  override fun addAndInvokeDraftStateListener(listener: () -> Unit) {
    var lastIsDraft = isDraft
    detailsModel.addListener {
      if (lastIsDraft != isDraft) listener()
      lastIsDraft = isDraft
    }
    listener()
  }

  override var mergeabilityState: GHPRMergeabilityState? = null
    private set
  override var mergeabilityLoadingError: Throwable? = null
    private set

  override var isBusy: Boolean by observableField(false, busyEventDispatcher)
  override var actionError: Throwable? by observableField(null, actionErrorEventDispatcher)

  init {
    stateData.loadMergeabilityState(disposable) {
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
    stateData.reloadMergeabilityState()
  }

  override fun submitCloseTask() = submitTask {
    stateData.close(EmptyProgressIndicator())
  }

  override fun submitReopenTask() = submitTask {
    stateData.reopen(EmptyProgressIndicator())
  }

  override fun submitMarkReadyForReviewTask() {
    stateData.markReadyForReview(EmptyProgressIndicator())
  }

  override fun submitMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    val dialog = ReviewMergeCommitMessageDialog(project,
                                                CollaborationToolsBundle.message("dialog.review.merge.commit.title"),
                                                GithubBundle.message("pull.request.merge.pull.request", details.number),
                                                details.title)
    if (!dialog.showAndGet()) {
      return@submitTask null
    }

    stateData.merge(EmptyProgressIndicator(), splitCommitMessage(dialog.message), mergeability.headRefOid)
  }

  override fun submitRebaseMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    stateData.rebaseMerge(EmptyProgressIndicator(), mergeability.headRefOid)
  }

  override fun submitSquashMergeTask() = submitTask {
    val mergeability = mergeabilityState ?: return@submitTask null
    changesData.loadCommitsFromApi().successOnEdt { commits ->
      val body = "* " + StringUtil.join(commits, { it.messageHeadline }, "\n\n* ")
      val dialog = ReviewMergeCommitMessageDialog(project,
                                                  CollaborationToolsBundle.message("dialog.review.merge.commit.title.with.squash"),
                                                  GithubBundle.message("pull.request.merge.pull.request", details.number),
                                                  body)
      if (!dialog.showAndGet()) {
        throw ProcessCanceledException()
      }
      dialog.message
    }.thenCompose { message ->
      stateData.squashMerge(EmptyProgressIndicator(), splitCommitMessage(message), mergeability.headRefOid)
    }
  }

  override fun submitTask(request: () -> CompletableFuture<*>?) {
    if (isBusy) return
    isBusy = true
    actionError = null

    val task = request()?.handleOnEdt { _, error ->
      actionError = error?.takeIf { !CompletableFutureUtil.isCancellation(it) }
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

  private fun splitCommitMessage(commitMessage: String): Pair<String, String> {
    val idx = commitMessage.indexOf("\n\n")
    return if (idx < 0) "" to commitMessage
    else {
      val subject = commitMessage.substring(0, idx)
      if (subject.contains("\n")) "" to commitMessage
      else subject to commitMessage.substring(idx + 2)
    }
  }
}