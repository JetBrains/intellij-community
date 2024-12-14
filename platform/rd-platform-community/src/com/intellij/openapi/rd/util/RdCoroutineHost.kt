// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.rd.util

import com.intellij.codeWithMe.ClientIdContextElementPrecursor
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.jetbrains.rd.util.threading.coroutines.RdCoroutineScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
class RdCoroutineHost(coroutineScope: CoroutineScope) : RdCoroutineScope() {
  companion object {
    @Suppress("OPT_IN_USAGE")
    val instance: RdCoroutineHost by lazy {
      val scope = ApplicationManager.getApplication().service<ScopeHolder>()
      val precursor = scope.scope.coroutineContext[ClientIdContextElementPrecursor]
      if (precursor == null) logger<RdCoroutineHost>().error("ClientIdContextElementPrecursor is missing inside scope for RdCoroutineHost. " +
                                                             "It's required for automatic `ClientId` propagation for `lifetime.coroutineScope.launch {}`")
      RdCoroutineHost(scope.scope).apply {
        override(this)
      }
    }

    fun init() {
      instance
    }

    @Deprecated("Use Dispatchers.IO or another dispatcher appropriate for your purposes")
    val processIODispatcher: ExecutorCoroutineDispatcher = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()
  }

  override val defaultContext: CoroutineContext = coroutineScope.coroutineContext

  @Deprecated("Use Dispatchers.EDT", ReplaceWith("Dispatchers.EDT", "kotlinx.coroutines.Dispatchers", "com.intellij.openapi.application.EDT"))
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