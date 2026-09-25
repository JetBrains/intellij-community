// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProcessHandlerFunctions")
package com.intellij.execution.process

import com.intellij.execution.KillableProcess
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Tries to terminate the process taking [ProcessHandler.detachIsDefault] option into account.
 * If termination is already in progress, tries to forcibly kill the process if possible.
 */
@OptIn(DelicateCoroutinesApi::class)
fun initiateProcessTermination(processHandler: ProcessHandler) {
  processHandler.putUserData(ProcessHandler.TERMINATION_REQUESTED, true)
  GlobalScope.childScope("Destroy " + processHandler.javaClass.name, Dispatchers.Default, true).launch {
    if (processHandler is KillableProcess && processHandler.isProcessTerminating) {
      // process termination was requested, but it's still alive
      // in this case 'force quit' will be performed
      processHandler.killProcess()
    }
    else {
      if (!processHandler.isProcessTerminated) {
        if (processHandler.detachIsDefault()) {
          processHandler.detachProcess()
        }
        else {
          processHandler.destroyProcess()
        }
      }
    }
  }
}
