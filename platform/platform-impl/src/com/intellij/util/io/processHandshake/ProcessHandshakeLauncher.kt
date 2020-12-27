// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.intellij.util.io.processHandshake

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.selects.select
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException


private val LOG = logger<ProcessHandshakeLauncher<*, *, *>>()

abstract class ProcessHandshakeLauncher<H, T : ProcessHandshakeTransport<H>, R> {
  fun launchDaemon(): R {
    return GlobalScope.async(Dispatchers.IO) {
      createHandshakeTransport().use { transport ->
        ensureActive()

        createProcessHandler(transport)
          .withOutputCaptured(SynchronizedProcessOutput()) processHandler@{ launcherOutput ->
            startNotify()

            val handshakeAsync = async(Dispatchers.IO) { transport.readHandshake() }
            val finishedAsync = launcherOutput.onFinished().asDeferred()

            val handshake = try {
              select<H?> {
                handshakeAsync.onAwait { it }
                finishedAsync.onAwait { handshakeFailed(transport, this@processHandler, launcherOutput) }
              }
              // premature EOF; give the launcher a chance to exit cleanly and collect the whole output
              ?: select<Nothing> {
                finishedAsync.onAwait { handshakeFailed(transport, this@processHandler, launcherOutput) }
                onTimeout(1000) {
                  launcherOutput.setTimeout()
                  handshakeFailed(transport, this@processHandler, launcherOutput)
                }
              }
            }
            catch (e: IOException) {
              handshakeFailed(transport, this, launcherOutput, e)
            }

            handshakeSucceeded(handshake, transport, this)
          }
      }
    }.awaitWithCheckCanceled()
  }

  protected abstract fun createHandshakeTransport(): T
  protected abstract fun createProcessHandler(transport: T): BaseOSProcessHandler

  protected abstract fun handshakeSucceeded(handshake: H,
                                            transport: T,
                                            processHandler: BaseOSProcessHandler): R

  protected abstract fun handshakeFailed(transport: T,
                                         processHandler: BaseOSProcessHandler,
                                         output: ProcessOutput,
                                         reason: @NlsContexts.DialogMessage String?): Nothing

  private fun handshakeFailed(transport: T,
                              processHandler: BaseOSProcessHandler,
                              output: ProcessOutput,
                              exception: IOException? = null): Nothing = synchronized(output) {
    val errorExitCodeString =
      if (output.isExitCodeSet && output.exitCode != 0) ProcessTerminatedListener.stringifyExitCode(output.exitCode)
      else null

    LOG.warn("Reading handshake failed", exception)
    if (errorExitCodeString != null) {
      LOG.warn("Process finished with exit code $errorExitCodeString")
    }
    LOG.warn("Process stderr:\n${output.stderr}")

    val reason = when {
      errorExitCodeString != null -> IdeBundle.message("finished.with.exit.code.text.message", errorExitCodeString)
      exception == null -> ProcessHandshakeBundle.message("dialog.message.process.handshake.failed.eof")
      else -> ProcessHandshakeBundle.message("dialog.message.process.handshake.failed.ioe")
    }
    handshakeFailed(transport, processHandler, output, reason)
  }

  protected fun createProcessHandler(transport: T,
                                     commandLine: GeneralCommandLine): BaseOSProcessHandler {
    return transport.createProcessHandler(commandLine).apply {
      addProcessListener(LoggingProcessListener)
    }
  }
}

private object LoggingProcessListener : ProcessAdapter() {
  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    LOG.info("Process [$outputType]: ${event.text.removeSuffix("\n")}")
  }

  override fun processTerminated(event: ProcessEvent) {
    val exitCodeString = ProcessTerminatedListener.stringifyExitCode(event.exitCode)
    LOG.info("Process terminated with exit code ${exitCodeString}")
  }
}

private fun <R> Deferred<R>.awaitWithCheckCanceled(): R = asCompletableFuture().awaitWithCheckCanceled()
private fun <R> CompletableFuture<R>.awaitWithCheckCanceled(): R {
  try {
    if (ApplicationManager.getApplication() == null) return join()
    return ProgressIndicatorUtils.awaitWithCheckCanceled(this)
  }
  catch (e: Throwable) {
    throw ExceptionUtil.findCause(e, ExecutionException::class.java)?.cause ?: e
  }
}

private inline fun <P : ProcessHandler, T : ProcessOutput, R> P.withOutputCaptured(output: T, block: P.(T) -> R): R {
  val capturingProcessListener = CapturingProcessAdapter(output)
  addProcessListener(capturingProcessListener)
  return try {
    block(output)
  }
  finally {
    removeProcessListener(capturingProcessListener)
  }
}
