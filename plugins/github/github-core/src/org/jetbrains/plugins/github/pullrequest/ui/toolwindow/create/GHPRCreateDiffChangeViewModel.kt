// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.model.AsyncDiffViewModel
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import git4idea.changes.createVcsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GHPRCreateDiffChangeViewModel(
  private val project: Project,
  cs: CoroutineScope,
  val change: RefComparisonChange,
) : AsyncDiffViewModel {
  private val reloadRequests = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  override val request: StateFlow<ComputedResult<DiffRequest>?> = computationStateFlow(reloadRequests.consumeAsFlow().withInitial(Unit)) {
    val changeDiffProducer = ChangeDiffRequestProducer.create(project, change.createVcsChange(project))
                             ?: error("Could not create diff producer from $change")
    coroutineToIndicator {
      changeDiffProducer.process(UserDataHolderBase(), ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator())
    }.apply {
      putUserData(RefComparisonChange.KEY, change)
    }
  }.stateIn(cs, SharingStarted.Lazily, null)

  override fun reloadRequest() {
    reloadRequests.trySend(Unit)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRCreateDiffChangeViewModel) return false

    if (project != other.project) return false
    if (change != other.change) return false

    return true
  }

  override fun hashCode(): Int {
    var result = project.hashCode()
    result = 31 * result + change.hashCode()
    return result
  }
}