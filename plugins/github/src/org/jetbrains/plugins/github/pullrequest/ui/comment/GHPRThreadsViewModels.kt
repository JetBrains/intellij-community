// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.threadsComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModelImpl

internal class GHPRThreadsViewModels(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
) {
  private val cs = parentCs.childScope(javaClass.name)
  val canComment: Boolean = dataProvider.reviewData.canComment()

  val compactThreads: StateFlow<Collection<GHPRCompactReviewThreadViewModel>> =
    dataProvider.reviewData.threadsComputationFlow
      .transformConsecutiveSuccesses(false) {
        mapDataToModel(GHPullRequestReviewThread::id,
                       { createThread(it) },
                       { update(it) })
      }.map { it.getOrNull().orEmpty() }
      .stateIn(cs, SharingStarted.Lazily, emptyList())

  private fun CoroutineScope.createThread(initialData: GHPullRequestReviewThread) =
    UpdateableGHPRCompactReviewThreadViewModel(project, this, dataContext, dataProvider, initialData)

  private val _newComments = MutableStateFlow<Map<GHPRReviewCommentPosition, GHPRReviewNewCommentEditorViewModelImpl>>(emptyMap())
  val newComments: StateFlow<Map<GHPRReviewCommentPosition, GHPRReviewNewCommentEditorViewModel>> = _newComments.asStateFlow()

  fun requestNewComment(location: GHPRReviewCommentPosition): GHPRReviewNewCommentEditorViewModel =
    _newComments.updateAndGet { currentNewComments ->
      if (!currentNewComments.containsKey(location)) {
        val vm = createNewCommentVm(location)
        currentNewComments + (location to vm)
      }
      else {
        currentNewComments
      }
    }[location]!!

  fun cancelNewComment(location: GHPRReviewCommentPosition) =
    _newComments.update {
      val oldVm = it[location]
      val newMap = it - location
      oldVm?.destroy()
      newMap
    }

  private fun createNewCommentVm(position: GHPRReviewCommentPosition) =
    GHPRReviewNewCommentEditorViewModelImpl(project, cs, dataProvider,
                                            dataContext.repositoryDataService.remoteCoordinates.repository,
                                            dataContext.securityService.currentUser,
                                            dataContext.avatarIconsProvider,
                                            position) {
      cancelNewComment(position)
    }
}
