// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState
import com.intellij.openapi.project.IncompleteDependenciesService.IncompleteDependenciesAccessToken
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IncompleteDependenciesServiceImpl : IncompleteDependenciesService {
  override val stateFlow = MutableStateFlow(DependenciesState.COMPLETE)
  private val tokens = HashSet<AccessToken>()

  @RequiresReadLock
  override fun getState(): DependenciesState {
    ThreadingAssertions.assertReadAccess() // @RequiresReadLock does nothing in Kotlin
    return stateFlow.value
  }

  @RequiresWriteLock
  override fun enterIncompleteState(): IncompleteDependenciesAccessToken {
    val token = object : IncompleteDependenciesAccessToken() {
      @RequiresWriteLock
      override fun finish() {
        deregisterToken(this)
      }
    }
    registerToken(token)
    return token
  }

  @RequiresWriteLock
  private fun registerToken(token: AccessToken) {
    ThreadingAssertions.assertWriteAccess() // @RequiresWriteLock does nothing in Kotlin
    synchronized(tokens) {
      val wasEmpty = tokens.isEmpty()
      tokens.add(token)
      if (wasEmpty) {
        stateFlow.update { DependenciesState.INCOMPLETE }
      }
    }
  }

  @RequiresWriteLock
  private fun deregisterToken(token: AccessToken) {
    ThreadingAssertions.assertWriteAccess() // @RequiresWriteLock does nothing in Kotlin
    synchronized(tokens) {
      tokens.remove(token)
      if (tokens.isEmpty()) {
        stateFlow.update { DependenciesState.COMPLETE }
      }
    }
  }
}
