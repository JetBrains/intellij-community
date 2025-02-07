// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class XMixedModeSuspendContextBase(
  val lowLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugSuspendContext: XSuspendContext,
) : XSuspendContext()