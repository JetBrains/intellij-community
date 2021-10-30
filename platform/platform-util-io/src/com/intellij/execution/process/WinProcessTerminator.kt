// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("WinProcessTerminator")

package com.intellij.execution.process

import com.intellij.openapi.util.Key
import java.util.concurrent.CompletableFuture

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
                                           processService: ProcessService,
                                           terminateGracefully: () -> Boolean = {
                                             processService.sendWinProcessCtrlC(processHandler.process)
                                           }): Boolean {
  val stdoutMatched: CompletableFuture<Void> = CompletableFuture()
  val processListener = object : ProcessAdapter() {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      // Need to match "Terminate batch job (Y/N)?" message, but it's localized. Let's match "?" only.
      if (ProcessOutputType.isStdout(outputType) && "?" in event.text) {
        processHandler.removeProcessListener(this)
        stdoutMatched.complete(null)
      }
    }
  }
  processHandler.addProcessListener(processListener)
  return terminateGracefully().also {
    if (it) {
      stdoutMatched.whenComplete { _, _ ->
        if (isCmdBatchFile(processHandler, processService) && processHandler.process.isAlive) {
          processHandler.process.destroy()
        }
      }
    }
    else {
      processHandler.removeProcessListener(processListener)
    }
  }
}

private fun isCmdBatchFile(processHandler: KillableProcessHandler, processService: ProcessService): Boolean {
  return processService.getCommand(processHandler.process).firstOrNull().let {
    it != null && (it.endsWith(".bat") || it.endsWith(".cmd") || it.endsWith("\\cmd.exe"))
  }
}
