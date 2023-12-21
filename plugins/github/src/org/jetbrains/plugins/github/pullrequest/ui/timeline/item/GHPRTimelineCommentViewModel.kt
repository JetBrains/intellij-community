// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item

import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider
import java.util.*

interface GHPRTimelineCommentViewModel {
  val author: GHActor
  val createdAt: Date

  val bodyHtml: StateFlow<@NlsSafe String>

  val isBusy: StateFlow<Boolean>

  val canDelete: Boolean

  val canEdit: Boolean
  val editVm: StateFlow<CodeReviewTextEditingViewModel?>

  fun editBody()

  fun delete()
}

class UpdateableGHPRTimelineCommentViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  private val commentsData: GHPRCommentsDataProvider,
  initialData: GHIssueComment,
  ghostUser: GHActor
) : GHPRTimelineCommentViewModel, GHPRTimelineItem.Comment {
  private val cs = parentCs.childScope(CoroutineName("GitHub Pull Request Timeline Comment View Model"))
  private val taskLauncher = SingleCoroutineLauncher(cs)

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
      commentsData.deleteComment(EmptyProgressIndicator(), dataState.value.id)
    }
  }

  fun update(data: GHIssueComment) {
    dataState.value = data
  }

  private inner class EditViewModel(initialText: String)
    : CodeReviewSubmittableTextViewModelBase(project, cs, initialText), CodeReviewTextEditingViewModel {
    override fun save() {
      submit { text ->
        val updated = commentsData.updateComment(EmptyProgressIndicator(), id, text).await()
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