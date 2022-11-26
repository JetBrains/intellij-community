// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd.util

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.rd.createLifetime
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import com.jetbrains.rd.framework.util.RdCoroutineScope
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class RdCoroutineHost(lifetime: Lifetime) : RdCoroutineScope(lifetime) {
  companion object {
    private val logger = logger<RdCoroutineHost>()

    val instance by lazy { RdCoroutineHost(application.createLifetime() /* When shutting down the application we have to cancel and wait for all coroutines */) }

    fun init() { instance }
    fun initAsync() = AppExecutorUtil.getAppExecutorService().execute { init() }

    val applicationThreadPool get() = Dispatchers.IO
    val processIODispatcher = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()
    val nonUrgentDispatcher = NonUrgentExecutor.getInstance().asCoroutineDispatcher()
  }

  override val defaultDispatcher: CoroutineContext get() = applicationThreadPool

  val uiDispatcher get() = Dispatchers.EDT

  val uiDispatcherWithInlining = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = invokeLater { block.run() }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
      if (!application.isDispatchThread)
        return true

      val modality = ModalityState.current()
      val transactionGuard = TransactionGuard.getInstance()
      if (transactionGuard.isWriteSafeModality(modality) && !transactionGuard.isWritingAllowed)
        return true

      return false
    }
  }

  val uiDispatcherAnyModality = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = invokeLater(ModalityState.any()) { block.run() }
    override fun isDispatchNeeded(context: CoroutineContext) = !application.isDispatchThread
  }

  init {
    override(lifetime, this)
  }

  override fun onException(throwable: Throwable) {
    if (throwable !is CancellationException && throwable !is ProcessCanceledException) {
      logger.error("Unhandled coroutine throwable", throwable)
    }
  }

  override fun shutdown() {
    try {
      runBlocking {
        coroutineContext[Job]!!.cancelAndJoin()
      }
    }
    catch (e: CancellationException) {
      // nothing
    }
    catch (e: ProcessCanceledException) {
      // nothing
    }
    catch (e: Throwable) {
      logger.error(e)
    }
  }
}