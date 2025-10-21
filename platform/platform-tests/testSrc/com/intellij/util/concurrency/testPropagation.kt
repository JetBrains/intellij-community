// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions

internal suspend fun doPropagationTest(submit: (() -> Unit) -> Unit) {
  return suspendCancellableCoroutine { continuation ->
    val element = TestElement("element")
    installThreadContext(element) {                                    // install context in calling thread
      submit {                                                         // switch to another thread
        val result: Result<Unit> = runCatching {
          Assertions.assertSame(element, currentThreadContext()[TestElementKey])  // the same element must be present in another thread context
        }
        continuation.resumeWith(result)
      }
    }
  }
}

/**
 * Regular IDE unit tests are executed in EDT, so this function takes care of launching coroutine code without
 * blocking the event loop.
 */
internal fun doPropagationApplicationTest(action: suspend () -> Unit) {
  ApplicationManager.getApplication().assertIsNonDispatchThread()
  runWithContextPropagationEnabled {
    val test = {
      timeoutRunBlocking {
        withContext(Dispatchers.EDT) {
          action()
        }
      }
    }
    test()
  }
}