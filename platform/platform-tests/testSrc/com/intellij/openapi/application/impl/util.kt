// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.progress.timeoutWaitUp
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LeakHunter
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Assertions.assertSame
import java.util.function.Supplier
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

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

fun Application.withModality(action: () -> Unit) {
  val modalEntity = Any()
  invokeAndWait(Runnable {
    LaterInvocator.enterModal(modalEntity)
  }, ModalityState.any())
  try {
    action()
  }
  finally {
    invokeAndWait(Runnable {
      LaterInvocator.leaveModal(modalEntity)
    }, ModalityState.any())
  }
}

fun assertReferenced(root: Any, referenced: Any) {
  lateinit var foundObject: Any
  val rootSupplier: Supplier<Map<Any, String>> = Supplier {
    mapOf(root to "root")
  }
  LeakHunter.processLeaks(rootSupplier, referenced.javaClass, null) { leaked, _ ->
    foundObject = leaked
    false
  }
  assertSame(referenced, foundObject)
}

/**
 * @see com.intellij.util.ui.UIUtil.pump
 */
suspend fun pumpEDT() {
  assert(!EDT.isCurrentThreadEdt())
  return suspendCancellableCoroutine { continuation ->
    SwingUtilities.invokeLater {
      continuation.resume(Unit)
    }
  }
}
