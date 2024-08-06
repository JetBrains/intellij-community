// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider

/**
 * Represents the whole right-side tool window displaying AI assisted review results.
 */
@Service(Service.Level.PROJECT)
class GHPRAIReviewToolwindowViewModel(
  private val project: Project, parentCs: CoroutineScope,
) {
  private val cs = parentCs.childScope(Dispatchers.Main)

  private val _activationRequests = MutableSharedFlow<Unit>(1)
  internal val activationRequests: Flow<Unit> = _activationRequests.asSharedFlow()

  private val _requestedReview = MutableSharedFlow<GHPRAiAssistantReviewViewModel>(1)
  internal val requestedReview: StateFlow<GHPRAiAssistantReviewViewModel?> = _requestedReview.stateIn(cs, Eagerly, null)

  fun activate() {
    _activationRequests.tryEmit(Unit)
  }

  fun requestReview(dataProvider: GHPRDataProvider, gitRepository: GitRepository) {
    activate()
    cs.launch {
      _requestedReview.emit(GHPRAiAssistantReviewViewModel(project, cs, dataProvider, gitRepository))
    }
  }
}