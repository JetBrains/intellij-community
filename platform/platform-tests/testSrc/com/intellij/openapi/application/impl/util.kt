// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.timeoutWaitUp
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun CoroutineScope.waitForPendingWrite(): Semaphore {
  val finishWrite = Semaphore(1)
  val pendingWrite = Semaphore(1)
  val listenerDisposable = Disposer.newDisposable()
  ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
    override fun beforeWriteActionStart(action: Any) {
      pendingWrite.up()
      finishWrite.timeoutWaitUp()
      Disposer.dispose(listenerDisposable)
    }
  }, listenerDisposable)
  launch(Dispatchers.EDT) {
    runWriteAction {}
  }
  pendingWrite.timeoutWaitUp()
  return finishWrite
}

internal fun CoroutineScope.waitForWrite(): Semaphore {
  val inWrite = Semaphore(1)
  val finishWrite = Semaphore(1)
  launch(Dispatchers.EDT) {
    runWriteAction {
      inWrite.up()
      finishWrite.timeoutWaitUp()
    }
  }
  inWrite.timeoutWaitUp()
  return finishWrite
}
