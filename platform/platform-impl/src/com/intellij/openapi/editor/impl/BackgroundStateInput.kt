// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

private val LOG: Logger = logger<BackgroundStateInput<*>>()

/**
 * An input of an [com.intellij.openapi.editor.state.ObservableState] that needs a read action, for example a value
 * that the PSI gives.
 *
 * A calculator of the state must not take the RW lock, because an editor can be built on `Dispatchers.UI`, which
 * forbids that lock (IJPL-243574). So [compute] runs in a background read action, and [update] gets the result on the
 * EDT. [update] stores the result and refreshes the properties that read it.
 *
 * A new computation cancels the previous one, because the previous one can have read the inputs before they changed.
 * A failed computation is logged, and it does not stop the next one.
 *
 * Unit-test mode computes the value at once, on the calling thread, unless a test calls [computeInBackgroundInTests].
 */
internal class BackgroundStateInput<T>(
  private val scope: CoroutineScope,
  private val compute: () -> T,
  private val update: (T) -> Unit,
) {
  private val jobRef: AtomicReference<Job?> = AtomicReference()

  /**
   * Computes the value again, and then gives it to [update].
   */
  fun recalculate() {
    if (!computesInBackground()) {
      val result = try {
        ApplicationManager.getApplication().runReadAction(Computable { compute() })
      } catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.error(e)
        return
      }
      update(result)
      return
    }
    val job = scope.launch(start = CoroutineStart.LAZY, context = ClientId.coroutineContext()) {
      val result = try {
        readAction {
          compute()
        }
      } catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.error(e)
        return@launch
      }
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement() + ClientId.coroutineContext()) {
        writeIntentReadAction {
          update(result)
        }
      }
    }
    jobRef.getAndSet(job)?.cancel()
    job.start()
  }

  /**
   * Waits until the last computation that was started ends.
   */
  @TestOnly
  suspend fun awaitForTests() {
    jobRef.get()?.join()
  }

  companion object {
    @Volatile private var inBackgroundInTests: Boolean = false

    /**
     * Returns `true` when inputs compute in the background. Outside unit-test mode, this is always so.
     */
    fun computesInBackground(): Boolean {
      return !ApplicationManager.getApplication().isUnitTestMode || inBackgroundInTests
    }

    /**
     * Makes inputs compute in the background, as in production, until [disposable] is disposed.
     */
    @TestOnly
    fun computeInBackgroundInTests(disposable: Disposable) {
      inBackgroundInTests = true
      Disposer.register(disposable) {
        inBackgroundInTests = false
      }
    }
  }
}
