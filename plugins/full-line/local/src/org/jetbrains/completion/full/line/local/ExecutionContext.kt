package org.jetbrains.completion.full.line.local

import io.kinference.model.ExecutionContext as KExecutionContext
import kotlin.coroutines.CoroutineContext

data class ExecutionContext(
  val coroutineContext: CoroutineContext,
  val checkCancelled: () -> Unit = { }
) {
  fun toInference() = KExecutionContext(coroutineContext, checkCancelled)
}
