// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.computationState
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.createThreadsRequestsFlow

internal class GHPRThreadsViewModels(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
) {
  private val cs = parentCs.childScope(classAsCoroutineName())

  val compactThreads: StateFlow<Collection<GHPRCompactReviewThreadViewModel>> =
    dataProvider.reviewData.createThreadsRequestsFlow()
      .computationState()
      .transformConsecutiveSuccesses(false) {
        mapDataToModel(GHPullRequestReviewThread::id,
                       { createThread(it) },
                       { update(it) })
      }.map { it.getOrNull().orEmpty() }
      .stateIn(cs, SharingStarted.Lazily, emptyList())

  private fun CoroutineScope.createThread(initialData: GHPullRequestReviewThread): UpdateableGHPRCompactReviewThreadViewModel {
    return UpdateableGHPRCompactReviewThreadViewModel(project, this, dataContext, dataProvider, initialData)
  }
}
