package org.jetbrains.completion.full.line.local

import org.jetbrains.completion.full.line.local.utils.FLCCDispatchers
import io.kinference.model.ExecutionContext as KExecutionContext
import kotlin.coroutines.CoroutineContext

data class ExecutionContext(
  val coroutineContext: CoroutineContext = FLCCDispatchers.models,
  val checkCancelled: () -> Unit = { }
) {
  fun toInference() = KExecutionContext(coroutineContext, checkCancelled)
}
