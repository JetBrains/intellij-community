// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaCoroutines")
@file:Experimental

package com.intellij.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Example:
 * ```
 * interface SomeExtensionPointInterface {
 *   suspend fun someSuspendingFunction(p: Params): String
 * }
 *
 * final class MyJavaImplementation implements SomeExtensionPointInterface {
 *   @Override
 *   public Object someSuspendingFunction(Params p, Continuation<String> $completion) {
 *     return JavaCoroutines.suspendJava(jc -> {
 *       executeOnPooledThread(() -> {
 *         jc.resume("Hello");
 *       });
 *     }, $completion);
 *   }
 * }
 * ```
 */
suspend fun <T> suspendJava(block: Consumer<JavaContinuation<T>>): T {
  return suspendCancellableCoroutine { continuation ->
    block.accept(JContinuationImpl(continuation))
  }
}

interface JavaContinuation<in T> {

  val context: CoroutineContext

  fun resume(value: T)

  fun resumeWithException(t: Throwable)

  fun cancel()
}

private class JContinuationImpl<T>(
  private val continuation: CancellableContinuation<T>
) : JavaContinuation<T> {

  override val context: CoroutineContext get() = continuation.context

  override fun resume(value: T) {
    continuation.resume(value)
  }

  override fun resumeWithException(t: Throwable) {
    continuation.resumeWithException(t)
  }

  override fun cancel() {
    continuation.cancel()
  }
}
