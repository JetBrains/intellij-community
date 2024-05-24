// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState
import com.intellij.openapi.project.IncompleteDependenciesService.IncompleteDependenciesAccessToken
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IncompleteDependenciesServiceImpl(private val project: Project) : IncompleteDependenciesService {
  override val stateFlow = MutableStateFlow(DependenciesState.COMPLETE)
  private val tokens = HashSet<IncompleteDependenciesAccessTokenImpl>()

  @RequiresReadLock
  override fun getState(): DependenciesState {
    ThreadingAssertions.assertReadAccess() // @RequiresReadLock does nothing in Kotlin
    return stateFlow.value
  }

  @RequiresWriteLock
  override fun enterIncompleteState(): IncompleteDependenciesAccessToken {
    return issueToken()
  }

  @RequiresWriteLock
  private fun issueToken(): IncompleteDependenciesAccessTokenImpl {
    ThreadingAssertions.assertWriteAccess() // @RequiresWriteLock does nothing in Kotlin
    synchronized(tokens) {
      val lastToken = tokens.lastOrNull()
      val stateBefore = if (lastToken == null) DependenciesState.COMPLETE else DependenciesState.INCOMPLETE
      val stateAfter = DependenciesState.INCOMPLETE

      val activity = IncompleteDependenciesModeStatisticsCollector.started(project, stateBefore, stateAfter)
      val token = IncompleteDependenciesAccessTokenImpl(activity)
      tokens.add(token)

      updateState(stateBefore, stateAfter)
      return token
    }
  }

  @RequiresWriteLock
  private fun deregisterToken(token: IncompleteDependenciesAccessTokenImpl) {
    ThreadingAssertions.assertWriteAccess() // @RequiresWriteLock does nothing in Kotlin
    synchronized(tokens) {
      tokens.remove(token)
      val stateBefore = DependenciesState.INCOMPLETE
      val stateAfter = if (tokens.isEmpty()) DependenciesState.COMPLETE else DependenciesState.INCOMPLETE
      IncompleteDependenciesModeStatisticsCollector.finished(token.activity, stateBefore, stateAfter)
      updateState(stateBefore, stateAfter)
    }
  }

  @RequiresWriteLock
  private fun updateState(stateBefore: DependenciesState, stateAfter: DependenciesState) {
    ThreadingAssertions.assertWriteAccess() // @RequiresWriteLock does nothing in Kotlin

    if (stateAfter != stateBefore) {
      PsiManager.getInstance(project).dropPsiCaches()
      stateFlow.update { stateAfter }
    }
  }

  private inner class IncompleteDependenciesAccessTokenImpl(val activity: StructuredIdeActivity) : IncompleteDependenciesAccessToken() {
    @RequiresWriteLock
    override fun finish() {
      deregisterToken(this)
    }
  }
}
