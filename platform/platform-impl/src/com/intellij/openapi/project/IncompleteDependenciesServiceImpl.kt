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
  private var incompleteModeActivity: StructuredIdeActivity? = null // atomic is not needed because reads/writes are guarded by synchronized

  @RequiresReadLock
  override fun getState(): DependenciesState {
    ThreadingAssertions.assertReadAccess() // @RequiresReadLock does nothing in Kotlin
    return stateFlow.value
  }

  @RequiresWriteLock
  override fun enterIncompleteState(requestor: Any): IncompleteDependenciesAccessToken {
    return issueToken(requestor.javaClass)
  }

  @RequiresWriteLock
  private fun issueToken(requestor: Class<*>): IncompleteDependenciesAccessTokenImpl {
    ThreadingAssertions.assertWriteAccess() // @RequiresWriteLock does nothing in Kotlin
    synchronized(tokens) {
      val lastToken = tokens.lastOrNull()
      val stateBefore = if (lastToken == null) DependenciesState.COMPLETE else DependenciesState.INCOMPLETE
      val stateAfter = DependenciesState.INCOMPLETE

      val currentIncompleteModeActivity = incompleteModeActivity
                                          ?: run {
                                            assert(stateBefore == DependenciesState.COMPLETE)
                                            val newActivity = IncompleteDependenciesModeStatisticsCollector.incompleteModeStarted(project)
                                            incompleteModeActivity = newActivity
                                            newActivity
                                          }

      val subtaskActivity = IncompleteDependenciesModeStatisticsCollector.incompleteModeSubtaskStarted(project, currentIncompleteModeActivity, requestor, stateBefore, stateAfter)
      val token = IncompleteDependenciesAccessTokenImpl(subtaskActivity, requestor)
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
      IncompleteDependenciesModeStatisticsCollector.incompleteModeSubtaskFinished(token.subtaskActivity, token.requestor, stateBefore, stateAfter)
      if (stateAfter == DependenciesState.COMPLETE) {
        assert(incompleteModeActivity != null)
        IncompleteDependenciesModeStatisticsCollector.incompleteModeFinished(incompleteModeActivity)
        incompleteModeActivity = null
      }
      updateState(stateBefore, stateAfter)
    }
  }

  @RequiresWriteLock
  private fun updateState(stateBefore: DependenciesState, stateAfter: DependenciesState) {
    ThreadingAssertions.assertWriteAccess() // @RequiresWriteLock does nothing in Kotlin

    if (stateAfter != stateBefore) {
      stateFlow.update { stateAfter }
      if (stateAfter.isComplete && !project.isDisposed) {
        PsiManager.getInstance(project).dropPsiCaches()
      }
    }
  }

  private inner class IncompleteDependenciesAccessTokenImpl(val subtaskActivity: StructuredIdeActivity, val requestor: Class<*>) : IncompleteDependenciesAccessToken() {
    @RequiresWriteLock
    override fun finish() {
      deregisterToken(this)
    }
  }
}
