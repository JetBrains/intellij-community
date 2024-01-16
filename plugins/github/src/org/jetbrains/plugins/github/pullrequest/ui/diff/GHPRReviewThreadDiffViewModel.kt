// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.UpdateableGHPRReviewThreadCommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewThreadEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewThreadEditorViewModel.CommentItem
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import kotlin.coroutines.cancellation.CancellationException

interface GHPRReviewThreadDiffViewModel : GHPRReviewThreadEditorViewModel {
  val isVisible: StateFlow<Boolean>
  val location: StateFlow<DiffLineLocation?>
}

private val LOG = logger<GHPRReviewThreadDiffViewModel>()

internal class UpdateableGHPRReviewThreadDiffViewModel(
  val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  initialMappedData: MappedThreadData
) : GHPRReviewThreadDiffViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Timeline Thread View Model"))
  private val reviewData: GHPRReviewDataProvider = dataProvider.reviewData
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val initialData = initialMappedData.data
  private val dataState = MutableStateFlow(initialData)
  private val mappedDataState = MutableStateFlow(initialMappedData)

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val id = initialData.id

  override val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider

  private val commentsVms = dataState
    .map { it.comments.withIndex() }
    .mapDataToModel({ it.value.id }, { createComment(it) }, { update(it) })
    .stateIn(cs, SharingStarted.Eagerly, emptyList())
  private val repliesFolded = MutableStateFlow(initialData.comments.size > 3)

  override val comments: StateFlow<List<CommentItem>> =
    commentsVms.combineState(repliesFolded) { comments, folded ->
      if (!folded || comments.size <= 3) {
        comments.map { CommentItem.Comment(it) }
      }
      else {
        listOf(
          CommentItem.Comment(comments.first()),
          CommentItem.Expander(comments.size - 2) { repliesFolded.value = false },
          CommentItem.Comment(comments.last())
        )
      }
    }

  override val canCreateReplies: StateFlow<Boolean> = dataState.mapState { it.viewerCanReply }
  private val _isWritingReply = MutableStateFlow(false)
  override val isWritingReply: StateFlow<Boolean> = _isWritingReply.asStateFlow()
  override val newReplyVm: GHPRNewThreadCommentViewModel = ReplyViewModel()

  override val canChangeResolvedState: StateFlow<Boolean> =
    dataState.mapState { it.viewerCanResolve || it.viewerCanUnresolve }
  override val isResolved: StateFlow<Boolean> = dataState.mapState { it.isResolved }

  override val isVisible: StateFlow<Boolean> = mappedDataState.mapState { it.isVisible }
  override val location: StateFlow<DiffLineLocation?> = mappedDataState.mapState { it.location }

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
          reviewData.unresolveThread(EmptyProgressIndicator(), id)
        }
        else {
          reviewData.resolveThread(EmptyProgressIndicator(), id)
        }.await()
      }
      catch (e: Exception) {
        if (e is ProcessCanceledException || e is CancellationException) return@launch
        LOG.warn("Failed to change thread resolution", e)
        return@launch
      }
      dataState.value = newData
    }
  }

  fun update(data: MappedThreadData) {
    dataState.value = data.data
    mappedDataState.value = data
  }

  private fun CoroutineScope.createComment(comment: IndexedValue<GHPullRequestReviewComment>): UpdateableGHPRReviewThreadCommentViewModel =
    UpdateableGHPRReviewThreadCommentViewModel(project, this, dataContext, dataProvider,
                                               this@UpdateableGHPRReviewThreadDiffViewModel, comment)

  private inner class ReplyViewModel
    : CodeReviewSubmittableTextViewModelBase(project, cs, ""), GHPRNewThreadCommentViewModel {

    override val currentUser: GHActor = dataContext.securityService.currentUser

    override fun submit() {
      val replyId = dataState.value.comments.firstOrNull()?.id ?: return
      submit {
        reviewData.addComment(EmptyProgressIndicator(), replyId, it).await()
        text.value = ""
      }
    }
  }

  data class MappedThreadData(
    val data: GHPullRequestReviewThread,
    val isVisible: Boolean,
    val location: DiffLineLocation?
  )
}
