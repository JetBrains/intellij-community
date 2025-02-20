// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.annotations.ApiStatus

/**
 * Provides methods needed for basic debugger actions support, such as all kind of stepping, pause, breakpoint hit, etc.
 */
@ApiStatus.Internal
interface XMixedModeDebugProcessExtension {
  fun getStoppedThreadId(context : XSuspendContext) : Long
  fun belongsToMe(frame: XStackFrame): Boolean
  fun belongsToMe(file: VirtualFile): Boolean
}