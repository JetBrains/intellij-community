// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item

import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentBodyViewModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import java.util.*

interface GHPRTimelineThreadCommentViewModel {
  val author: GHActor
  val createdAt: Date

  val bodyVm: GHPRReviewCommentBodyViewModel

  val isBusy: StateFlow<Boolean>

  val canEdit: Boolean
  fun editBody()
  val editVm: StateFlow<CodeReviewTextEditingViewModel?>

  val canDelete: Boolean
  fun delete()
}

class UpdateableGHPRTimelineThreadCommentViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  thread: UpdateableGHPRTimelineThreadViewModel,
  initialData: GHPullRequestReviewComment
) : GHPRTimelineThreadCommentViewModel {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Timeline Thread Comment View Model"))
  private val reviewData = dataProvider.reviewData
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val dataState = MutableStateFlow(initialData)

  private val id = initialData.id
  override val author: GHActor = initialData.author ?: dataContext.securityService.ghostUser
  override val createdAt: Date = initialData.createdAt

  override val bodyVm: GHPRReviewCommentBodyViewModel =
    GHPRReviewCommentBodyViewModel(cs, project, dataContext, dataProvider, thread.id, id)

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val canDelete: Boolean = initialData.viewerCanDelete
  override val canEdit: Boolean = initialData.viewerCanUpdate

  private val _editVm = MutableStateFlow<EditViewModel?>(null)
  override val editVm: StateFlow<CodeReviewTextEditingViewModel?> = _editVm.asStateFlow()

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
      reviewData.deleteComment(EmptyProgressIndicator(), dataState.value.id)
    }
  }

  fun update(data: GHPullRequestReviewComment) {
    dataState.value = data
  }

  private inner class EditViewModel(initialText: String)
    : CodeReviewSubmittableTextViewModelBase(project, cs, initialText), CodeReviewTextEditingViewModel {
    override fun save() {
      submit { text ->
        val updated = reviewData.updateComment(EmptyProgressIndicator(), id, text).await()
        dataState.update {
          updated
        }
        stopEditing()
      }
    }

    override fun stopEditing() = this@UpdateableGHPRTimelineThreadCommentViewModel.stopEditing()

    fun dispose() = cs.cancel()
  }
}