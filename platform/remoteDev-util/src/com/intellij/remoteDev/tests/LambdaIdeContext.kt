package com.intellij.remoteDev.tests

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
@ApiStatus.Internal
interface LambdaIdeContext: CoroutineScope {
  fun addPostCleanup(action: () -> Unit) {
    coroutineContext.job.invokeOnCompletion {
      action()
    }
  }
}

@ApiStatus.Internal
interface LambdaMonolithContext: LambdaBackendContext, LambdaFrontendContext
@ApiStatus.Internal
interface LambdaBackendContext: LambdaIdeContext
@ApiStatus.Internal
interface LambdaFrontendContext: LambdaIdeContext

@ApiStatus.Internal
class LambdaBackendContextClass(override val coroutineContext: CoroutineContext) : LambdaBackendContext

@ApiStatus.Internal
class LambdaFrontendContextClass(override val coroutineContext: CoroutineContext) : LambdaFrontendContext

@ApiStatus.Internal
class LambdaMonolithContextClass(override val coroutineContext: CoroutineContext) : LambdaMonolithContext