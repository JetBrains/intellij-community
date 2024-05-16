// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsViewModel
import java.util.*

interface GHPRTimelineCommentViewModel {
  val author: GHActor
  val createdAt: Date

  val bodyHtml: StateFlow<@NlsSafe String>

  val isBusy: StateFlow<Boolean>

  val canDelete: Boolean

  val canEdit: Boolean
  val editVm: StateFlow<CodeReviewTextEditingViewModel?>

  val canReact: Boolean
  val reactionsVm: GHReactionsViewModel

  fun editBody()

  fun delete()
}

internal class UpdateableGHPRTimelineCommentViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  private val commentsData: GHPRCommentsDataProvider,
  initialData: GHIssueComment
) : GHPRTimelineCommentViewModel, GHPRTimelineItem.Comment {
  private val cs = parentCs.childScope("GitHub Pull Request Timeline Comment View Model")
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val currentUser: GHUser = dataContext.securityService.currentUser
  private val ghostUser: GHUser = dataContext.securityService.ghostUser
  private val reactionIconsProvider: IconsProvider<GHReactionContent> = dataContext.reactionIconsProvider

  private val dataState = MutableStateFlow(initialData)

  private val id = initialData.id
  override val author: GHActor = initialData.author ?: ghostUser
  override val createdAt: Date = initialData.createdAt

  override val bodyHtml: StateFlow<String> = dataState.map {
    it.body.convertToHtml(project)
  }.stateIn(cs, SharingStarted.Eagerly, "")

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val canDelete: Boolean = initialData.viewerCanDelete
  override val canEdit: Boolean = initialData.viewerCanUpdate
  override val canReact: Boolean = initialData.viewerCanReact

  private val _editVm = MutableStateFlow<EditViewModel?>(null)
  override val editVm: StateFlow<CodeReviewTextEditingViewModel?> = _editVm.asStateFlow()

  private val reactions: StateFlow<List<GHReaction>> = dataState.mapState { it.reactions.nodes }
  override val reactionsVm: GHReactionsViewModel = GHReactionViewModelImpl(
    cs, id, reactions, currentUser, dataContext.reactionsService, reactionIconsProvider
  )

  override fun editBody() {
    val currentText = dataState.value.body
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
      commentsData.deleteComment(dataState.value.id)
    }
  }

  fun update(data: GHIssueComment) {
    dataState.value = data
  }

  private inner class EditViewModel(initialText: String)
    : CodeReviewSubmittableTextViewModelBase(project, cs, initialText), CodeReviewTextEditingViewModel {
    override fun save() {
      submit { text ->
        val updated = commentsData.updateComment(id, text)
        dataState.update {
          it.copy(body = updated)
        }
        stopEditing()
      }
    }

    override fun stopEditing() = this@UpdateableGHPRTimelineCommentViewModel.stopEditing()

    fun dispose() = cs.cancel()
  }
}