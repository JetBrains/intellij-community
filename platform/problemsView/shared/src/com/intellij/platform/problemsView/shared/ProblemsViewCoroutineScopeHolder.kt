// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Service
@ApiStatus.Internal
class ProblemsViewCoroutineScopeHolder(private val myScope: CoroutineScope) {
  fun createNamedChildScope(name: String): CoroutineScope {
    return myScope.childScope(name = name)
  }

  fun launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
  ): Job {
    return myScope.launch(context, start, block)
  }

  companion object {
    fun getInstance(): ProblemsViewCoroutineScopeHolder = service()
  }
}
