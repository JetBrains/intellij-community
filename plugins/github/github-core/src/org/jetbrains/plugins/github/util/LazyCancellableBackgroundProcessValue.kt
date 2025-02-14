// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.cancellationOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.EventDispatcher
import com.intellij.collaboration.ui.SimpleEventListener
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

@ApiStatus.ScheduledForRemoval
@Deprecated("Please migrate to coroutines")
abstract class LazyCancellableBackgroundProcessValue<T> private constructor()
  : ClearableLazyValue<CompletableFuture<T>>() {

  private val dropEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  private var progressIndicator = NonReusableEmptyProgressIndicator()
  private var computationId: UUID? = null

  private var overriddenFuture: CompletableFuture<T>? = null

  override fun compute(): CompletableFuture<T> {
    val overriddenFuture = overriddenFuture
    val future = if (overriddenFuture == null) {
      computeUnderProgress()
    }
    else {
      /**
       * new future will be completed successfully if [overriddenFuture] completes successfully
       * otherwise the default computation [computeUnderProgress] will be started and it's results will be passed to this future
       */
      val newFuture = CompletableFuture<T>()
      overriddenFuture.handle(BiFunction<T?, Throwable?, Unit> { result, error ->
        if (error != null) {
          runInEdt(ModalityState.any()) {
            this.overriddenFuture = null
            computeUnderProgress().handle(BiFunction<T?, Throwable?, Unit> { result, error ->
              if (error != null) {
                newFuture.completeExceptionally(CompletableFutureUtil.extractError(error))
              }
              else {
                newFuture.complete(result as T)
              }
            })
          }
        }
        else newFuture.complete(result as T)
      })
      newFuture
    }

    // avoid dropping the same value twice
    val currentComputationId = UUID.randomUUID()
    computationId = currentComputationId
    return future.cancellationOnEdt {
      if (computationId == currentComputationId) drop()
    }
  }

  private fun computeUnderProgress(): CompletableFuture<T> {
    progressIndicator = NonReusableEmptyProgressIndicator()
    val indicator = progressIndicator
    return compute(indicator)
  }

  abstract fun compute(indicator: ProgressIndicator): CompletableFuture<T>

  override fun drop() {
    overriddenFuture = null
    super.drop()
    computationId = null
    progressIndicator.cancel()
    dropEventDispatcher.multicaster.eventOccurred()
  }

  companion object {
    fun <T> create(progressManager: ProgressManager, computer: (ProgressIndicator) -> T) =
      object : LazyCancellableBackgroundProcessValue<T>() {
        override fun compute(indicator: ProgressIndicator) = progressManager.submitIOTask(indicator, computer)
      }

    fun <T> create(computer: (ProgressIndicator) -> CompletableFuture<T>) =
      object : LazyCancellableBackgroundProcessValue<T>() {
        override fun compute(indicator: ProgressIndicator): CompletableFuture<T> = computer(indicator)
      }
  }
}