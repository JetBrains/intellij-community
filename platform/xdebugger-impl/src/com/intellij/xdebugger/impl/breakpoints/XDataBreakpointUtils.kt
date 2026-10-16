// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.platform.debugger.impl.shared.BreakpointRequestCounter.Companion.REQUEST_IS_NOT_NEEDED
import com.intellij.xdebugger.breakpoints.XDataBreakpointAccessType
import com.intellij.xdebugger.breakpoints.XDataBreakpointHitCountMode
import com.intellij.xdebugger.breakpoints.XDataBreakpointLifetime
import com.intellij.xdebugger.breakpoints.XDataBreakpointProperties
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XBreakpointBase<*, *, *>.setDataBreakpointAccessType(
  accessType: XDataBreakpointAccessType?,
  requestId: Long = REQUEST_IS_NOT_NEEDED,
) {
  val dataProperties = properties as? XDataBreakpointProperties ?: return
  val changed = dataProperties.dataAccessType != accessType
  dataProperties.dataAccessType = accessType
  notifyExternalPropertyUpdateRequested(requestId, changed)
}

@ApiStatus.Internal
fun XBreakpointBase<*, *, *>.setDataBreakpointLifetime(
  lifetime: XDataBreakpointLifetime?,
  requestId: Long = REQUEST_IS_NOT_NEEDED,
) {
  val dataProperties = properties as? XDataBreakpointProperties ?: return
  val changed = dataProperties.dataLifetime != lifetime
  dataProperties.dataLifetime = lifetime
  notifyExternalPropertyUpdateRequested(requestId, changed)
}

@ApiStatus.Internal
fun XBreakpointBase<*, *, *>.setDataBreakpointHitCount(
  enabled: Boolean,
  mode: XDataBreakpointHitCountMode,
  value: Int,
  requestId: Long = REQUEST_IS_NOT_NEEDED,
) {
  val dataProperties = properties as? XDataBreakpointProperties ?: return
  val changed = dataProperties.hitCountEnabled != enabled || dataProperties.dataHitCountMode != mode ||
                dataProperties.hitCountValue != value
  dataProperties.hitCountEnabled = enabled
  dataProperties.dataHitCountMode = mode
  dataProperties.hitCountValue = value
  notifyExternalPropertyUpdateRequested(requestId, changed)
}
