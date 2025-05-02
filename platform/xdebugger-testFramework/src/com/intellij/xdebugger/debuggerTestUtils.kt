// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.await
import java.util.concurrent.ExecutionException

@Suppress("RAW_RUN_BLOCKING")
internal fun toggleBreakpoint(project: Project, file: VirtualFile, line: Int): XLineBreakpoint<*>? = runBlocking {
  val debuggerUtil = XDebuggerUtil.getInstance() as XDebuggerUtilImpl
  val breakpointPromise = writeAction {
    debuggerUtil.toggleAndReturnLineBreakpoint(project, file, line, false)
  }
  try {
    withTimeout(XDebuggerTestUtil.TIMEOUT_MS.toLong()) {
      breakpointPromise.await()
    }
  }
  catch (e: TimeoutCancellationException) {
    throw RuntimeException(e)
  }
  catch (e: ExecutionException) {
    throw RuntimeException(e.cause)
  }
}
