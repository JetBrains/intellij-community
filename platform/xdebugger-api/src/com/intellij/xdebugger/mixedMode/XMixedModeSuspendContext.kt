// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.annotations.ApiStatus

/**
 * In mixed mode we need to keep suspend contexts of both debug processes to support features of those processes.
 * Also, we refer to the contexts when building a mixed call stack
 */
@ApiStatus.Internal
abstract class XMixedModeSuspendContextBase(
  val lowLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugSuspendContext: XSuspendContext,
) : XSuspendContext()