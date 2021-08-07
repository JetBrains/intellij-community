// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin

import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence

@ApiStatus.Experimental
sealed class CommitCheckRunState {
  object NotStarted : CommitCheckRunState()

  object Started : CommitCheckRunState()

  data class Running(
    val name: @Nls(capitalization = Sentence) String,
    val details: @Nls(capitalization = Sentence) String,
    val progress: Double
  ) : CommitCheckRunState()

  object Completed : CommitCheckRunState()
}

@ApiStatus.Experimental
abstract class BaseCommitCheck<T : CommitProblem> : CheckinHandler(), CommitCheck<T> {
  private val _runState = MutableStateFlow<CommitCheckRunState>(CommitCheckRunState.NotStarted)

  val runState: StateFlow<CommitCheckRunState> = _runState.asStateFlow()

  override suspend fun runCheck(indicator: ProgressIndicator): T? =
    coroutineScope {
      runState
        .onEach { updateIndicator(indicator, it) }
        .launchIn(this + CoroutineName("${this@BaseCommitCheck} progress tracker"))

      _runState.value = CommitCheckRunState.Started
      try {
        val problem = doRunCheck()
        coroutineContext.cancelChildren() // cancel progress tracking on success
        problem
      }
      finally {
        _runState.value = CommitCheckRunState.Completed
      }
    }

  protected abstract suspend fun doRunCheck(): T?

  protected fun progress(
    name: @Nls(capitalization = Sentence) String? = null,
    details: @Nls(capitalization = Sentence) String? = null,
    progress: Double? = null
  ) {
    val state = _runState.value as? CommitCheckRunState.Running

    val newName = name ?: state?.name
    val newDetails = details ?: state?.details.takeIf { name == null }
    val newProgress = progress ?: state?.progress

    _runState.value = CommitCheckRunState.Running(newName.orEmpty(), newDetails.orEmpty(), newProgress ?: 0.0)
  }

  private fun updateIndicator(indicator: ProgressIndicator, state: CommitCheckRunState) {
    if (state is CommitCheckRunState.Running) {
      indicator.text = state.name
      indicator.text2 = state.details
      indicator.fraction = state.progress
    }
  }
}