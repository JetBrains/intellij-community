// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.collaboration.async.withInitial
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlin.coroutines.cancellation.CancellationException

internal object GitLabCoroutineUtil {
  @OptIn(ExperimentalCoroutinesApi::class)
  fun <T> batchesResultsFlow(reloadSignal: Flow<Unit>, flowSource: () -> Flow<List<T>>) =
    reloadSignal.withInitial(Unit).transformLatest {
      try {
        flowSource().collect {
          emit(Result.success(it))
        }
      }
      catch (ce: CancellationException) {
        currentCoroutineContext().ensureActive()
        // ignore
      }
      catch (e: Exception) {
        emit(Result.failure(e))
      }
    }
}