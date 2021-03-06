// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DelayedTaskScheduler(private val delaySeconds: Long,
                           private val disposable: Disposable,
                           private val task: () -> Unit) {

  private var scheduler: ScheduledFuture<*>? = null

  init {
    Disposer.register(disposable, Disposable {
      stop()
    })
  }

  @RequiresEdt
  fun start() {
    if (Disposer.isDisposed(disposable)) error("Already disposed")

    if (scheduler == null) {
      scheduler = EdtScheduledExecutorService.getInstance().scheduleWithFixedDelay(task, delaySeconds, delaySeconds, TimeUnit.SECONDS)
    }
  }

  @RequiresEdt
  fun stop() {
    scheduler?.cancel(true)
    scheduler = null
  }
}