// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SoftlyKillableProcessHandler {
  /**
   * Prevents force process termination, if returns `true`.
   * For example, if it returns `false`, [com.intellij.debugger.impl.DebuggerManagerImpl.attachVirtualMachine] could stop a JVM process
   * by terminating its VM with -1 exit code.
   */
  fun shouldKillProcessSoftly(): Boolean
}