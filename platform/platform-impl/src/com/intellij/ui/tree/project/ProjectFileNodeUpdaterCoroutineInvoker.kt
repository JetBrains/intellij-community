// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.project

import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

internal class ProjectFileNodeUpdaterCoroutineInvoker(private val coroutineScope: CoroutineScope) : ProjectFileNodeUpdaterInvoker {
  private val semaphore = Semaphore(1)

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Disposer.dispose(this)
    }
  }

  override fun invoke(runnable: Runnable): Promise<*>? {
    val job = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      semaphore.withPermit {
        yield()
        runnable.run()
      }
    }
    return job.asPromise()
  }

  override fun invokeLater(runnable: Runnable, delay: Int) {
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      delay(delay.toLong())
      semaphore.withPermit {
        yield()
        runnable.run()
      }
    }
  }

  override fun dispose() { }
}
