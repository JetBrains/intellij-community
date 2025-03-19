// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.threadsComputationFlow
import java.util.*

interface GHPRTimelineReviewViewModel {
  val author: GHActor
  val createdAt: Date

  val state: GHPullRequestReviewState

  val bodyHtml: StateFlow<@NlsSafe String>

  val isBusy: StateFlow<Boolean>

  val canEdit: StateFlow<Boolean>
  val editVm: StateFlow<CodeReviewTextEditingViewModel?>

  val threads: StateFlow<ComputedResult<List<GHPRTimelineThreadViewModel>>>

  fun editBody()
}

internal class UpdateableGHPRTimelineReviewViewModel internal constructor(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  initialData: GHPullRequestReview
) : GHPRTimelineReviewViewModel,
    GHPRTimelineItem.Review {
  private val cs = parentCs.childScope("GitHub Pull Request Timeline Review View Model")
  private val reviewData = dataProvider.reviewData
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val dataState = MutableStateFlow(initialData)

  private val id = initialData.id
  override val author: GHActor = initialData.author ?: dataContext.securityService.ghostUser
  override val createdAt: Date = initialData.createdAt
  override val state: GHPullRequestReviewState = initialData.state

  override val threads: StateFlow<ComputedResult<List<GHPRTimelineThreadViewModel>>> =
    reviewData.createThreadsVmsFlow().stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  override val bodyHtml: StateFlow<String> = dataState.map {
    it.body.convertToHtml(project)
  }.stateIn(cs, SharingStarted.Eagerly, "")

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  // GH fails to edit reviews that are initialized with an empty body.
  // bodyHtml will contain <body></body>, so it's less easy to check for emptiness.
  override val canEdit: StateFlow<Boolean> = dataState.mapState { it.body.isNotEmpty() && initialData.viewerCanUpdate }

  private val _editVm = MutableStateFlow<EditViewModel?>(null)
  override val editVm: StateFlow<CodeReviewTextEditingViewModel?> = _editVm.asStateFlow()

  val showDiffRequests = MutableSharedFlow<ChangesSelection>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

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

  fun update(data: GHPullRequestReview) {
    dataState.value = data
  }

  private fun GHPRReviewDataProvider.createThreadsVmsFlow() =
    threadsComputationFlow.transformConsecutiveSuccesses {
      map { threads -> threads.filter { it.reviewId == id } }.mapDataToModel({ it.id }, { createThread(it) }, { update(it) })
    }

  private fun CoroutineScope.createThread(data: GHPullRequestReviewThread): UpdateableGHPRTimelineThreadViewModel {
    val threadVm = UpdateableGHPRTimelineThreadViewModel(project, this, dataContext, dataProvider, data)
    launchNow {
      threadVm.showDiffRequests.collect(showDiffRequests)
    }
    return threadVm
  }

  private inner class EditViewModel(initialText: String)
    : CodeReviewSubmittableTextViewModelBase(project, cs, initialText), CodeReviewTextEditingViewModel {
    override fun save() {
      submit { text ->
        val updated = reviewData.updateReviewBody(id, text)
        dataState.update {
          it.copy(body = updated)
        }
        stopEditing()
      }
    }

    override fun stopEditing() = this@UpdateableGHPRTimelineReviewViewModel.stopEditing()

    fun dispose() = cs.cancel()
  }
}