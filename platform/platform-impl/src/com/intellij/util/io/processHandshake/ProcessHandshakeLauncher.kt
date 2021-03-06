// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.intellij.util.io.processHandshake

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.selects.select
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.CompletableFuture


private val LOG = logger<ProcessHandshakeLauncher<*, *, *>>()

abstract class ProcessHandshakeLauncher<H, T : ProcessHandshakeTransport<H>, R> {
  /**
   * The launcher process might exit without waiting for the target process to finish,
   * in which case it can also exit before the target process writes the handshake response.
   * This timeout gives the handshake a chance to be read even after the launcher process has finished.
   */
  protected open val handshakeTimeoutAfterLauncherFinishedMillis: Long = 5000

  /**
   * Upon a handshake failure the launcher might have generated (or relayed from the target process)
   * some output useful for error handling or general debug logging.
   * This timeout gives the launcher process some time to exit cleanly after the handshake has failed.
   */
  protected open val launcherOutputTimeoutAfterHandshakeFailedMillis: Long = 1000

  fun launchWithProgress(progressTitle: @NlsContexts.ProgressTitle String): R {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
      launchAsync().awaitWithCheckCanceled()
    }, progressTitle, true, null)
  }

  fun launchAsync(): Deferred<R> {
    return GlobalScope.async(Dispatchers.IO) {
      launch()
    }
  }

  suspend fun launch(): R = supervisorScope {
    createHandshakeTransport().use { transport ->
      ensureActive()

      createProcessHandler(transport).withOutputCaptured(SynchronizedProcessOutput()) processHandler@{ launcherOutput ->
        startNotify()

        val handshakeAsync = async(Dispatchers.IO) { transport.readHandshake() }
        val finishedAsync = launcherOutput.onFinished().asDeferred()

        val handshake: H = try {
          select {
            handshakeAsync.onAwait { it }
            finishedAsync.onAwait { null }
          }
          // the launcher exited without waiting for the target process to finish, the handshake may still arrive
          ?: select {
            handshakeAsync.onAwait { it }
            handshakeTimeoutAfterLauncherFinishedMillis.takeUnless { it < 0 }?.let { timeoutMs ->
              onTimeout(timeoutMs) { null }
            }
          }
          ?: throw EOFException()
        }
        catch (e: IOException) {
          // give the launcher a chance to exit cleanly (in case it hasn't yet) to collect the whole output
          select<Unit> {
            finishedAsync.onAwait { }
            launcherOutputTimeoutAfterHandshakeFailedMillis.takeUnless { it < 0 }?.let { timeoutMs ->
              onTimeout(timeoutMs) { launcherOutput.setTimeout() }
            }
          }
          handshakeFailed(transport, this@processHandler, launcherOutput, e.takeUnless { it is EOFException })
        }

        handshakeSucceeded(handshake, transport, this)
      }
    }
  }

  protected abstract fun createHandshakeTransport(): T
  protected abstract fun createProcessHandler(transport: T): BaseOSProcessHandler

  protected abstract fun handshakeSucceeded(handshake: H,
                                            transport: T,
                                            processHandler: BaseOSProcessHandler): R

  protected open fun handshakeFailed(transport: T,
                                     processHandler: BaseOSProcessHandler,
                                     output: ProcessOutput,
                                     reason: @NlsContexts.DialogMessage String): Nothing {
    throw ExecutionException(reason)
  }

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
    throw ExceptionUtil.findCause(e, java.util.concurrent.ExecutionException::class.java)?.cause ?: e
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
