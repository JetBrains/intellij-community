// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull

class GHPRDetailsTimelineViewModel internal constructor(private val project: Project,
                                                        parentCs: CoroutineScope,
                                                        private val dataContext: GHPRDataContext,
                                                        private val dataProvider: GHPRDataProvider) {
  private val cs = parentCs.childScope()

  val details: StateFlow<ComputedResult<GHPRDetailsFull>> = channelFlow<ComputedResult<GHPRDetailsFull>> {
    val disposable = Disposer.newDisposable()
    dataProvider.detailsData.loadDetails(disposable) {
      if (!it.isDone) {
        trySend(ComputedResult.loading())
      }
      it.handle { res, err ->
        if (err != null && !CompletableFutureUtil.isCancellation(err)) {
          trySend(ComputedResult.failure(err.cause ?: err))
        }
        else {
          val details = createDetails(res)
          trySend(ComputedResult.success(details))
        }
      }
    }
    awaitClose { Disposer.dispose(disposable) }
  }.stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  private val _descriptionEditVm = MutableStateFlow<GHPREditDescriptionViewModel?>(null)
  val descriptionEditVm: StateFlow<GHPREditDescriptionViewModel?> = _descriptionEditVm.asStateFlow()

  private fun createDetails(data: GHPullRequest): GHPRDetailsFull = GHPRDetailsFull(
    dataProvider.id,
    data.url,
    data.author ?: dataContext.securityService.ghostUser,
    data.createdAt,
    data.title.convertToHtml(project),
    data.body,
    data.body.convertToHtml(project),
    data.viewerCanUpdate
  )

  fun editDescription() {
    if (_descriptionEditVm.value != null) return
    val details = details.value.getOrNull() ?: return
    val description = details.description.orEmpty()
    _descriptionEditVm.value = GHPREditDescriptionViewModel(project, cs, dataProvider.detailsData, description) {
      _descriptionEditVm.update {
        it?.dispose()
        null
      }
    }
  }
}

class GHPREditDescriptionViewModel(project: Project,
                                   parentCs: CoroutineScope,
                                   private val detailsData: GHPRDetailsDataProvider,
                                   initialText: String,
                                   private val onDone: () -> Unit)
  : CodeReviewSubmittableTextViewModelBase(project, parentCs, initialText) {
  fun save() {
    submit {
      detailsData.updateDetails(EmptyProgressIndicator(), description = it).await()
      onDone()
    }
  }

  fun cancelEditing() {
    onDone()
  }

  internal fun dispose() {
    cs.cancel()
  }
}