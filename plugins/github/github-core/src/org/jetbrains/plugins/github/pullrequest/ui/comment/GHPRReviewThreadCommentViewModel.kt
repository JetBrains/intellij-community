// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHReaction
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentBodyViewModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsViewModel
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import java.util.*

interface GHPRReviewThreadCommentViewModel {
  val avatarIconsProvider: GHAvatarIconsProvider

  val author: GHActor
  val createdAt: Date

  val isPending: StateFlow<Boolean>
  val isFirstInResolvedThread: StateFlow<Boolean>

  val bodyVm: GHPRReviewCommentBodyViewModel

  val isBusy: StateFlow<Boolean>

  val canEdit: Boolean
  fun editBody()
  val editVm: StateFlow<CodeReviewTextEditingViewModel?>

  val canReact: Boolean
  val reactionsVm: GHReactionsViewModel

  val canDelete: Boolean
  fun delete()
}

internal class UpdateableGHPRReviewThreadCommentViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  thread: GHPRReviewThreadViewModel,
  initialDataWithIndex: IndexedValue<GHPullRequestReviewComment>
) : GHPRReviewThreadCommentViewModel {
  private val cs = parentCs.childScope("GitHub Pull Request Thread Comment View Model")
  private val reviewData = dataProvider.reviewData
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val currentUser: GHUser = dataContext.securityService.currentUser
  override val avatarIconsProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider
  private val reactionIconsProvider: IconsProvider<GHReactionContent> = dataContext.reactionIconsProvider

  private val initialData = initialDataWithIndex.value
  private val dataState = MutableStateFlow(initialDataWithIndex)

  private val id = initialData.id
  override val author: GHActor = initialData.author ?: dataContext.securityService.ghostUser
  override val createdAt: Date = initialData.createdAt

  override val isPending: StateFlow<Boolean> = dataState.mapState { it.value.state == GHPullRequestReviewCommentState.PENDING }
  override val isFirstInResolvedThread: StateFlow<Boolean> = dataState.combineState(thread.isResolved) { (index, _), resolved ->
    index == 0 && resolved
  }

  override val bodyVm: GHPRReviewCommentBodyViewModel =
    GHPRReviewCommentBodyViewModel(cs, project, dataContext, dataProvider, thread.id, id)

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val canDelete: Boolean = initialData.viewerCanDelete
  override val canEdit: Boolean = initialData.viewerCanUpdate
  override val canReact: Boolean = initialData.viewerCanReact

  private val _editVm = MutableStateFlow<EditViewModel?>(null)
  override val editVm: StateFlow<CodeReviewTextEditingViewModel?> = _editVm.asStateFlow()

  private val reactions: StateFlow<List<GHReaction>> = dataState.mapState { it.value.reactions.nodes }
  override val reactionsVm: GHReactionsViewModel = GHReactionViewModelImpl(
    cs, id, reactions, currentUser, dataContext.reactionsService, reactionIconsProvider
  )

  override fun editBody() {
    val currentText = dataState.value.value.body
    _editVm.update {
      (it ?: EditViewModel(currentText)).apply {
        requestFocus()
      }
    }
  }

  private fun stopEditing() {
    _editVm.update {
      it?.dispose()
      null
    }
  }

  override fun delete() {
    taskLauncher.launch {
      reviewData.deleteComment(id)
    }
  }

  fun update(data: IndexedValue<GHPullRequestReviewComment>) {
    dataState.value = data
  }

  private inner class EditViewModel(initialText: String)
    : CodeReviewSubmittableTextViewModelBase(project, cs, initialText), CodeReviewTextEditingViewModel {
    override fun save() {
      submit { text ->
        val updated = reviewData.updateComment(id, text)
        dataState.update {
          it.copy(value = updated)
        }
        stopEditing()
      }
    }

    override fun stopEditing() = this@UpdateableGHPRReviewThreadCommentViewModel.stopEditing()

    fun dispose() = cs.cancel()
  }
}