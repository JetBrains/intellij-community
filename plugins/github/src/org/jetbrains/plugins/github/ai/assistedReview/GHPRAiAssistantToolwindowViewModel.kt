// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider

@Service(Service.Level.PROJECT)
class GHPRAiAssistantToolwindowViewModel(
  private val project: Project, parentCs: CoroutineScope
) {
  private val cs = parentCs.childScope(Dispatchers.Main)

  private val _activationRequests = MutableSharedFlow<Unit>(1)
  internal val activationRequests: Flow<Unit> = _activationRequests.asSharedFlow()


  private val _requestedReview = MutableSharedFlow<GHPRAiAssistantReviewVm>(1)
  internal val requestedReview: Flow<GHPRAiAssistantReviewVm> = _requestedReview.asSharedFlow()

  fun activate() {
    _activationRequests.tryEmit(Unit)
  }

  internal fun requestReview(dataProvider: GHPRDataProvider) {
    activate()
    cs.launch {
      _requestedReview.tryEmit(GHPRAiAssistantReviewVm(project, cs, dataProvider))
    }
  }
}