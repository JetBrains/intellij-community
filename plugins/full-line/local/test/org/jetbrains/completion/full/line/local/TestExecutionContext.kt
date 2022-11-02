package org.jetbrains.completion.full.line.local

import kotlinx.coroutines.Dispatchers

object TestExecutionContext {
  val default = ExecutionContext(Dispatchers.Default, checkCancelled = {})
}
