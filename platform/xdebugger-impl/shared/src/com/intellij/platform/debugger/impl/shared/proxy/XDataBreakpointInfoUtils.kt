// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.platform.debugger.impl.rpc.XDataBreakpointInfo
import com.intellij.xdebugger.breakpoints.XDataBreakpointProperties
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XDataBreakpointProperties.toDataBreakpointInfo(): XDataBreakpointInfo {
  return XDataBreakpointInfo(
    expressionPresentation = expressionPresentation,
    sizeBytes = sizeBytes,
    registrationError = registrationError,
    accessType = dataAccessType,
    lifetime = dataLifetime,
    hitCountEnabled = hitCountEnabled,
    hitCountMode = dataHitCountMode,
    hitCountValue = hitCountValue,
  )
}
