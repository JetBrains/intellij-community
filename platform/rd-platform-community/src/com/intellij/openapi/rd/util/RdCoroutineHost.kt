// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.rd.util

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.jetbrains.rd.util.threading.coroutines.RdCoroutineScope
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class RdCoroutineHost(coroutineScope: CoroutineScope) : RdCoroutineScope() {
  companion object {
    val instance by lazy {
      val scope = ApplicationManager.getApplication().service<ScopeHolder>()
      RdCoroutineHost(scope.scope).apply {
        override(this)
      }
    }

    fun init() {
      instance
    }

    val processIODispatcher: ExecutorCoroutineDispatcher = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()
  }

  override val defaultContext: CoroutineContext = coroutineScope.coroutineContext

  val uiDispatcher: CoroutineContext
    get() = Dispatchers.EDT

  @Deprecated("This is a deprecated dispatcher used before Dispatchers.EDT. Please switch to the Dispatchers.EDT")
  val uiDispatcherWithInlining = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      ApplicationManager.getApplication().invokeLater(block, ModalityState.defaultModalityState())
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
      val application = ApplicationManager.getApplication()
      if (!application.isDispatchThread || !application.isWriteIntentLockAcquired) {
        return true
      }

      val modality = ModalityState.current()
      val transactionGuard = TransactionGuard.getInstance()
      return transactionGuard.isWriteSafeModality(modality) && !transactionGuard.isWritingAllowed
    }
  }

  @Deprecated("Dispatchers.EDT + ModalityState.any().asContextElement()", ReplaceWith("Dispatchers.EDT + ModalityState.any().asContextElement()", "kotlinx.coroutines.Dispatchers",
                                   "com.intellij.openapi.application.EDT", "com.intellij.openapi.application.ModalityState",
                                   "com.intellij.openapi.application.asContextElement"))
  val uiDispatcherAnyModality: CoroutineContext
    get() = Dispatchers.EDT + ModalityState.any().asContextElement()

  override fun onException(throwable: Throwable) {
    if (throwable !is CancellationException && throwable !is ProcessCanceledException) {
      logger<RdCoroutineHost>().error("Unhandled coroutine throwable", throwable)
    }
  }

  @Service(Service.Level.APP)
  private class ScopeHolder(@JvmField val scope: CoroutineScope)
}