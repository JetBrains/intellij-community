// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.codeWithMe.clientIdContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import kotlin.coroutines.EmptyCoroutineContext

internal interface InvokerDelegate : Disposable {
  val description: String
  fun offer(runnable: Runnable, delay: Int, promise: Promise<*>)
  fun run(task: Runnable, promise: AsyncPromise<*>): Boolean
}

// TODO: the service should be per-client and take ClientId from the injected scope
@Service(Service.Level.APP)
internal class InvokerService(private val scope: CoroutineScope) {
  companion object {
    @JvmStatic fun getInstance(): InvokerService = service()
  }

  fun forEdt(description: String): InvokerDelegate =
    if (useCoroutineInvoker) {
      val clientIdContextElement = currentThreadContext().clientIdContextElement ?: EmptyCoroutineContext
      EdtCoroutineInvokerDelegate(description, scope.childScope(description, clientIdContextElement))
    }
    else {
      EdtLegacyInvokerDelegate(description)
    }

  fun forBgt(description: String, useReadAction: Boolean, maxThreads: Int): InvokerDelegate =
    if (useCoroutineInvoker) {
      val clientIdContextElement = currentThreadContext().clientIdContextElement ?: EmptyCoroutineContext
      if (maxThreads == 1) {
        SequentialBgtCoroutineInvokerDelegate(description, scope.childScope(description, clientIdContextElement), useReadAction)
      }
      else {
        ConcurrentBgtCoroutineInvokerDelegate(description, scope.childScope(description, clientIdContextElement), useReadAction, maxThreads)
      }
    }
    else {
      BgtLegacyInvokerDelegate(description, useReadAction, maxThreads)
    }

  private val useCoroutineInvoker: Boolean
    get() = Registry.`is`("ide.tree.invoker.use.coroutines", true)
}
