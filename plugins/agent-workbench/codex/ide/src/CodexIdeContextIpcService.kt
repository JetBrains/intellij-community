// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<CodexIdeContextIpcService>()
private val IPC_RETRY_DELAY = 10.seconds

@Service(Service.Level.APP)
internal class CodexIdeContextIpcService(serviceScope: CoroutineScope) {
  private val collector = CodexIdeContextCollector()
  private val protocol = CodexIdeContextIpcProtocol(collector::collect)

  @Volatile
  private var activeTransport: CodexIdeContextIpcTransport? = null

  private val job = serviceScope.launch(CoroutineName("Codex IDE context IPC"), start = CoroutineStart.LAZY) {
    runServerLoop()
  }

  init {
    registerShutdownOnCancellation(serviceScope) { shutdown() }
  }

  fun ensureStarted() {
    if (!job.start() && job.isCancelled) {
      LOG.debug { "Codex IDE context IPC service is cancelled" }
    }
  }

  private suspend fun runServerLoop() {
    while (true) {
      currentCoroutineContext().ensureActive()
      val transport = createCodexIdeContextIpcTransport()
      activeTransport = transport
      try {
        transport.serve(protocol)
      }
      catch (e: CodexIdeContextIpcAddressInUseException) {
        LOG.info(e.message)
        delay(IPC_RETRY_DELAY)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.warn("Codex IDE context IPC server failed", e)
        delay(IPC_RETRY_DELAY)
      }
      finally {
        activeTransport = null
        transport.close()
      }
    }
  }

  private fun shutdown() {
    activeTransport?.close()
    job.cancel()
  }
}

internal class CodexIdeContextIpcStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    serviceAsync<CodexIdeContextIpcService>().ensureStarted()
  }
}

@OptIn(AwaitCancellationAndInvoke::class)
private fun registerShutdownOnCancellation(scope: CoroutineScope, onShutdown: suspend CoroutineScope.() -> Unit) {
  val job = scope.coroutineContext[Job]
  if (job == null) {
    LOG.warn("Codex IDE context IPC service scope has no Job; shutdown hook not installed")
    return
  }
  scope.awaitCancellationAndInvoke(CoroutineName("Codex IDE context IPC shutdown") + Dispatchers.IO) {
    LOG.debug { "Codex IDE context IPC service scope cancelling; shutting down server" }
    onShutdown()
  }
}
