// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.testFramework.LeakHunter
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertNotNull
import java.lang.Runnable
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
  val foundObjects = ArrayList<Any>()
  val rootSupplier: Supplier<Map<Any, String>> = Supplier {
    mapOf(root to "root")
  }
  LeakHunter.processLeaks(rootSupplier, referenced.javaClass, null) { leaked, _ ->
    foundObjects.add(leaked)
    true
  }
  assertNotNull(foundObjects.find { it === referenced })
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

internal suspend fun withDifferentInitialModalities(action: suspend CoroutineScope.() -> Unit) {
  coroutineScope {
    action()
    withContext(ModalityState.any().asContextElement()) {
      action()
    }
    withContext(ModalityState.NON_MODAL.asContextElement()) {
      action()
    }
  }
}

internal suspend fun processApplicationQueue() {
  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {}
}
