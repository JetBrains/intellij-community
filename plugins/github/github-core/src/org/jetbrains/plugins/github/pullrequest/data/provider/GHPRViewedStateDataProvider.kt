// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ComputedResult
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState

interface GHPRViewedStateDataProvider {
  val viewedStateNeedsReloadSignal: Flow<Unit>

  suspend fun loadViewedState(): Map<String, GHPullRequestFileViewedState>

  suspend fun updateViewedState(paths: Iterable<String>, isViewed: Boolean)

  suspend fun signalViewedStateNeedsReload()
}

internal val GHPRViewedStateDataProvider.viewedStateComputationState: Flow<ComputedResult<Map<String, GHPullRequestFileViewedState>>>
  get() = computationStateFlow(viewedStateNeedsReloadSignal.withInitial(Unit)) { loadViewedState() }