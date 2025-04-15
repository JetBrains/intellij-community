// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WinProcessTerminator")
@file:Internal

package com.intellij.execution.process

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Terminates the Windows process gracefully via sending Ctrl+C event. If it's a batch script, then it will be terminated
 * forcibly after appearing the infamous "Terminate batch job (Y/N)?" prompt. The thing is that Ctrl+C event is delivered
 * to both the batch script and a child process spawned by the script. When the child process inside the batch script is terminated,
 * Command Prompt interprets the received Ctrl+C as a request to interrupt the batch script and shows the prompt even if
 * the end of the script has already been reached.
 *
 * Unfortunately, there is no way to suppress it in Command Prompt. Let's terminate the batch script forcibly when the prompt pops up.
 * See the discussion [disable/enable 'Terminate batch job (Y/N)?' confirmation](https://github.com/microsoft/terminal/issues/217)
 *
 * @return true if graceful termination has been performed (however the process may be still alive)
 */
@JvmOverloads
internal fun terminateWinProcessGracefully(processHandler: KillableProcessHandler,
                                           processService: LocalProcessService,
                                           terminateGracefully: () -> Boolean = {
                                             processService.sendWinProcessCtrlC(processHandler.process)
                                           }): Boolean {
  val questionFoundOrTerminated: CompletableFuture<Void> = CompletableFuture()
  val processListener = object : ProcessAdapter() {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      // Need to match "Terminate batch job (Y/N)?" message, but it's localized. Let's match "?" only.
      if (ProcessOutputType.isStdout(outputType) && "?" in event.text) {
        processHandler.removeProcessListener(this)
        questionFoundOrTerminated.complete(null)
      }
    }

    override fun processTerminated(event: ProcessEvent) {
      processHandler.removeProcessListener(this)
      questionFoundOrTerminated.complete(null)
    }
  }
  processHandler.addProcessListener(processListener)
  return terminateGracefully().also {
    if (it) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        if (isCmdBatchFile(processHandler, processService)) {
          awaitBatchQuestionAndDestroyInTests(questionFoundOrTerminated, processHandler)
        }
        processHandler.removeProcessListener(processListener)
        return@also
      }
      questionFoundOrTerminated.whenComplete { _, _ ->
        if (!processHandler.isProcessTerminated && isCmdBatchFile(processHandler, processService)) {
          destroy(processHandler)
        }
      }
    }
    else {
      processHandler.removeProcessListener(processListener)
    }
  }
}

private fun awaitBatchQuestionAndDestroyInTests(questionFoundOrTerminated: CompletableFuture<Void>,
                                                processHandler: KillableProcessHandler) {
  try {
    questionFoundOrTerminated.get(10, TimeUnit.SECONDS)
    destroy(processHandler)
  }
  catch (_: Exception) {
    // "Terminate batch job (Y/N)?" message hasn't been printed => the application might still be alive.
    // Graceful termination is done here. Now the process should be stopped forcibly.
    logger<KillableProcessHandler>().info("Process hasn't been terminated gracefully: couldn't find \"Terminate batch job (Y/N)?\".")
  }
}

private fun destroy(processHandler: KillableProcessHandler) {
  processHandler.process.destroy() // If the process is not alive, no action is taken.
}

private fun isCmdBatchFile(processHandler: KillableProcessHandler, processService: LocalProcessService): Boolean {
  return processService.getCommand(processHandler.process).firstOrNull().let {
    it != null && (it.endsWith(".bat") || it.endsWith(".cmd") || it.endsWith("\\cmd.exe"))
  }
}
