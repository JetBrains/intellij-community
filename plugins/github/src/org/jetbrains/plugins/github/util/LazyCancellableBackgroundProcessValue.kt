// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import java.util.concurrent.CompletableFuture

abstract class LazyCancellableBackgroundProcessValue<T> private constructor()
  : ClearableLazyValue<CompletableFuture<T>>() {

  private val dropEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  private var progressIndicator = NonReusableEmptyProgressIndicator()

  override fun compute(): CompletableFuture<T> {
    progressIndicator = NonReusableEmptyProgressIndicator()
    val indicator = progressIndicator
    return compute(indicator)
  }

  abstract fun compute(indicator: ProgressIndicator): CompletableFuture<T>

  override fun drop() {
    super.drop()
    progressIndicator.cancel()
    dropEventDispatcher.multicaster.eventOccurred()
  }

  fun addDropEventListener(disposable: Disposable, listener: () -> Unit) =
    SimpleEventListener.addDisposableListener(dropEventDispatcher, disposable, listener)

  companion object {
    fun <T> create(progressManager: ProgressManager, computer: (ProgressIndicator) -> T) =
      object : LazyCancellableBackgroundProcessValue<T>() {
        override fun compute(indicator: ProgressIndicator) = progressManager.submitIOTask(indicator, computer)
      }

    fun <T> create(computer: (ProgressIndicator) -> CompletableFuture<T>) =
      object : LazyCancellableBackgroundProcessValue<T>() {
        override fun compute(indicator: ProgressIndicator) = computer(indicator)
      }
  }
}