// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.model.AsyncDiffViewModel
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.createVcsChange
import git4idea.changes.getDiffComputer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn

interface GitLabMergeRequestDiffChangeViewModel : AsyncDiffViewModel {
  val change: RefComparisonChange
}

internal class GitLabMergeRequestDiffChangeViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val allChanges: GitBranchComparisonResult,
  override val change: RefComparisonChange,
) : GitLabMergeRequestDiffChangeViewModel {
  private val cs = parentCs.childScope(javaClass.name)
  private val reloadRequests = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

  override val request: StateFlow<ComputedResult<DiffRequest>?> = computationStateFlow(reloadRequests.consumeAsFlow().withInitial(Unit)) {
    val changeDiffProducer = ChangeDiffRequestProducer.create(project, change.createVcsChange(project))
                             ?: error("Could not create diff producer from $change")
    val request = coroutineToIndicator {
      changeDiffProducer.process(UserDataHolderBase(), ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator())
    }
    request.apply {
      putUserData(RefComparisonChange.KEY, change)
      putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, allChanges.patchesByChange[change]?.getDiffComputer())
    }
  }.stateIn(cs, SharingStarted.Lazily, null)

  override fun reloadRequest() {
    reloadRequests.trySend(Unit)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabMergeRequestDiffChangeViewModelImpl) return false

    if (project != other.project) return false
    if (allChanges != other.allChanges) return false
    if (change != other.change) return false

    return true
  }

  override fun hashCode(): Int {
    var result = project.hashCode()
    result = 31 * result + allChanges.hashCode()
    result = 31 * result + change.hashCode()
    return result
  }
}