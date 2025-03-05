// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.combineStateIn
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import kotlin.coroutines.cancellation.CancellationException

/**
 * A viewmodel for a compact foldable review thread view
 */
interface GHPRCompactReviewThreadViewModel : GHPRReviewThreadViewModel,
                                             CodeReviewResolvableItemViewModel {
  override val isBusy: StateFlow<Boolean>

  val comments: StateFlow<List<CommentItem>>

  override val canCreateReplies: StateFlow<Boolean>
  val isWritingReply: StateFlow<Boolean>
  override val newReplyVm: GHPRNewThreadCommentViewModel

  fun startWritingReply()
  fun stopWritingReply()

  override val isResolved: StateFlow<Boolean>
  override val canChangeResolvedState: StateFlow<Boolean>
  override fun changeResolvedState()

  sealed interface CommentItem {
    data class Comment(val vm: GHPRReviewThreadCommentViewModel) : CommentItem
    data class Expander(val collapsedCount: Int, val expand: () -> Unit) : CommentItem
  }
}

private val LOG = logger<UpdateableGHPRCompactReviewThreadViewModel>()

internal class UpdateableGHPRCompactReviewThreadViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  initialData: GHPullRequestReviewThread
) : GHPRCompactReviewThreadViewModel {
  private val cs = parentCs.childScope(javaClass.name)
  private val reviewData: GHPRReviewDataProvider = dataProvider.reviewData
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val dataState = MutableStateFlow(initialData)

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val id = initialData.id

  override val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider

  private val repliesFolded = MutableStateFlow(initialData.comments.size > 3)

  override val canCreateReplies: StateFlow<Boolean> = dataState.mapState { it.viewerCanReply }
  private val _isWritingReply = MutableStateFlow(false)
  override val isWritingReply: StateFlow<Boolean> = _isWritingReply.asStateFlow()
  override val newReplyVm: GHPRNewThreadCommentViewModel = ReplyViewModel()

  override val canChangeResolvedState: StateFlow<Boolean> =
    dataState.mapState { it.viewerCanResolve || it.viewerCanUnresolve }
  override val isResolved: StateFlow<Boolean> = dataState.mapState { it.isResolved }

  // have to do it LAST, bc comment VMs depend on thread fields
  // so they have to be initialized first
  private val commentsVms = dataState
    .map { it.comments.withIndex() }
    .mapDataToModel({ it.value.id }, { createComment(it) }, { update(it) })
    .stateIn(cs, SharingStarted.Eagerly, emptyList())
  override val comments = combineStateIn(cs, commentsVms, repliesFolded) { comments, folded ->
    if (!folded || comments.size <= 3) {
      comments.map { GHPRCompactReviewThreadViewModel.CommentItem.Comment(it) }
    }
    else {
      listOf(
        GHPRCompactReviewThreadViewModel.CommentItem.Comment(comments.first()),
        GHPRCompactReviewThreadViewModel.CommentItem.Expander(comments.size - 2) { repliesFolded.value = false },
        GHPRCompactReviewThreadViewModel.CommentItem.Comment(comments.last())
      )
    }
  }

  override fun startWritingReply() {
    _isWritingReply.value = true
    newReplyVm.requestFocus()
  }

  override fun stopWritingReply() {
    _isWritingReply.value = false
  }

  override fun changeResolvedState() {
    val resolved = isResolved.value
    taskLauncher.launch {
      val newData = try {
        if (resolved) {
          reviewData.unresolveThread(id)
        }
        else {
          reviewData.resolveThread(id)
        }
      }
      catch (e: Exception) {
        if (e is ProcessCanceledException || e is CancellationException) return@launch
        LOG.warn("Failed to change thread resolution", e)
        return@launch
      }
      dataState.value = newData
    }
  }

  fun update(data: GHPullRequestReviewThread) {
    dataState.value = data
  }

  private fun CoroutineScope.createComment(comment: IndexedValue<GHPullRequestReviewComment>): UpdateableGHPRReviewThreadCommentViewModel =
    UpdateableGHPRReviewThreadCommentViewModel(project, this, dataContext, dataProvider,
                                               this@UpdateableGHPRCompactReviewThreadViewModel, comment)

  private inner class ReplyViewModel
    : CodeReviewSubmittableTextViewModelBase(project, cs, ""), GHPRNewThreadCommentViewModel {

    override val currentUser: GHActor = dataContext.securityService.currentUser

    override fun submit() {
      val replyId = dataState.value.comments.firstOrNull()?.id ?: return
      submit {
        reviewData.addComment(replyId, it)
        text.value = ""
      }
    }
  }
}