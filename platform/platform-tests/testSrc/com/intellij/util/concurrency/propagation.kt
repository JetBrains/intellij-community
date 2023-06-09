// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Assertions

internal suspend fun doPropagationTest(submit: (() -> Unit) -> Unit) {
  return suspendCancellableCoroutine { continuation ->
    val element = TestElement("element")
    installThreadContext(element).use {                               // install context in calling thread
      submit {                                                         // switch to another thread
        val result: Result<Unit> = runCatching {
          Assertions.assertSame(element, currentThreadContext()[TestElementKey])  // the same element must be present in another thread context
        }
        continuation.resumeWith(result)
      }
    }
  }
}