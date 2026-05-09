package com.intellij.grazie.utils

import com.intellij.openapi.progress.util.runWithCheckCanceled
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * @see [com.intellij.openapi.progress.util.runWithCheckCanceled]
 * @see [com.intellij.util.io.computeDetached]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class FirstInvocationCancellationGuard {

  private val shouldRunWithCancellation = AtomicBoolean(true)

  fun <T> withCheckCancelled(
    context: CoroutineContext = EmptyCoroutineContext,
    block: () -> T,
  ): T {
    if (!shouldRunWithCancellation.get()) return block()
    val result = runWithCheckCanceled(context) {
      block()
    }
    shouldRunWithCancellation.set(false)
    return result
  }
}