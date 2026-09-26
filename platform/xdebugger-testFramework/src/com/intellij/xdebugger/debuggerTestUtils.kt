// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.EDT
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.isPending
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.milliseconds

internal fun toggleBreakpoint(project: Project, file: VirtualFile, line: Int): XLineBreakpoint<*>? {
  val debuggerUtil = XDebuggerUtil.getInstance() as XDebuggerUtilImpl
  val promise = runReadActionBlocking {
    debuggerUtil.toggleAndReturnLineBreakpoint(project, file, line, false)
  }
  return if (EDT.isCurrentThreadEdt()) {
    PlatformTestUtil.waitWithEventsDispatching({ "Failed to await breakpoint toggling" },
                                               { !promise.isPending },
                                               XDebuggerTestUtil.TIMEOUT_MS.milliseconds.inWholeSeconds.toInt())
    promise.blockingGet(0)
  }
  else {
    runBlockingMaybeCancellable {
      try {
        withTimeout(XDebuggerTestUtil.TIMEOUT_MS.milliseconds) {
          promise.await()
        }
      }
      catch (e: TimeoutCancellationException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        throw RuntimeException(e.cause)
      }
    }
  }
}
