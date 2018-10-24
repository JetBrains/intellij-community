// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.util.NonReusableEmptyProgressIndicator
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Future

abstract class SingleWorkerProcessExecutor(private val progressManager: ProgressManager, name: String) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(name, 1)
  private var progressIndicator: EmptyProgressIndicator = NonReusableEmptyProgressIndicator()

  private val processStateEventDispatcher = EventDispatcher.create(ProcessStateListener::class.java)

  @CalledInAwt
  protected fun <T> submit(task: (indicator: ProgressIndicator) -> T): Future<T> {
    val indicator = progressIndicator
    return executor.submit(Callable {
      indicator.checkCanceled()
      try {
        runInEdt { processStateEventDispatcher.multicaster.processStarted() }
        progressManager.runProcess(Computable { task(indicator) }, indicator)
      }
      finally {
        runInEdt { processStateEventDispatcher.multicaster.processFinished() }
      }
    })
  }

  @CalledInAwt
  protected fun cancelCurrentTasks() {
    progressIndicator.cancel()
    progressIndicator = NonReusableEmptyProgressIndicator()
  }

  fun addProcessListener(listener: ProcessStateListener, disposable: Disposable) =
    processStateEventDispatcher.addListener(listener, disposable)

  override fun dispose() {
    progressIndicator.cancel()
    executor.shutdownNow()
  }

  interface ProcessStateListener : EventListener {
    fun processStarted() {}
    fun processFinished() {}
  }
}