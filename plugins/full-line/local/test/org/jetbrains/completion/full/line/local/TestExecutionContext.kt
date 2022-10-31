package org.jetbrains.completion.full.line.local

import io.kinference.model.ExecutionContext
import kotlinx.coroutines.Dispatchers

object TestExecutionContext {
  val default = ExecutionContext(Dispatchers.Default, checkCancelled = {})
}
