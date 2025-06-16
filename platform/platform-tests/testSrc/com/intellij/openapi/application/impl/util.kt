// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.testFramework.LeakHunter
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.function.Supplier
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.test.assertFalse

fun Application.withModality(action: () -> Unit) {
  val modalEntity = Any()
  invokeAndWait(ContextAwareRunnable {
    LaterInvocator.enterModal(modalEntity)
  }, ModalityState.any())
  try {
    action()
  }
  finally {
    // This runnable should not be a target of prompt cancellation,
    // hence the `ContextAwareRunnable`
    invokeAndWait(ContextAwareRunnable {
      LaterInvocator.leaveModal(modalEntity)
    }, ModalityState.any())
  }
}

fun assertReferenced(root: Any, referenced: Any) {
  val rootSupplier: Supplier<Map<Any, String>> = Supplier {
    mapOf(root to "root")
  }
  assertFalse(LeakHunter.processLeaks (rootSupplier, referenced.javaClass, null, null) { leaked, _ ->
    leaked !== referenced
  })
}

fun assertNotReferenced(root: Any, referenced: Any) {
  LeakHunter.checkLeak(root, referenced::class.java) { potentialLeak ->
    potentialLeak === referenced
  }
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

internal suspend fun processApplicationQueue() {
  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {}
}
