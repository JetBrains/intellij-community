// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.LeakHunter
import com.intellij.util.ui.EDT
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Assertions.assertSame
import java.util.function.Supplier
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

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
