package ml.intellij.nlc.local

import io.kinference.model.ExecutionContext
import kotlinx.coroutines.Dispatchers

object TestExecutionContext {
    val default = ExecutionContext(Dispatchers.Default, checkCancelled = {})
}
