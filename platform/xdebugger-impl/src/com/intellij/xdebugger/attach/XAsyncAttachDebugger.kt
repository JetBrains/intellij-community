// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSessionStartedResult
import org.jetbrains.annotations.ApiStatus

/**
 * An [XAttachDebugger] that reports the debug session it starts.
 *
 * [XAttachDebugger.attachDebugSession] returns nothing. A caller that needs the new session must guess which
 * session belongs to its request. An implementation of this interface returns the session, so the caller does not guess.
 */
@ApiStatus.Experimental
interface XAsyncAttachDebugger : XAttachDebugger {
  /**
   * Attaches this debugger to the specified process and returns the started session.
   * The debugger is guaranteed to be returned by {@link XAttachDebuggerProvider#getAvailableDebuggers} for the specified process.
   *
   * @param attachHost  host (environment) on which the process runs
   * @param processInfo process to attach to
   * @throws ExecutionException if an error occurs during attach
   */
  @Throws(ExecutionException::class)
  suspend fun attachDebugSessionAsync(project: Project, attachHost: XAttachHost, processInfo: ProcessInfo): XSessionStartedResult
}
