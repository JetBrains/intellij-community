// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XBreakpointDto(
  val displayText: String,
  val userDescription: String?,
  val iconId: IconId,
  val enabled: Boolean,
  val sourcePosition: XSourcePositionDto?,
  val isDefault: Boolean,
  val suspendPolicy: SuspendPolicy,
  val logMessage: Boolean,
  val logStack: Boolean,
  val logExpressionObject: XExpressionDto?,
  val conditionExpression: XExpressionDto?,
)

@ApiStatus.Internal
fun XBreakpointBase<*, *, *>.toRpc(): XBreakpointDto {
  return XBreakpointDto(
    displayText = XBreakpointUtil.getShortText(this),
    userDescription = userDescription,
    iconId = getIcon().rpcId(),
    enabled = isEnabled,
    sourcePosition = sourcePosition?.toRpc(),
    isDefault = XDebuggerManager.getInstance(project).breakpointManager.isDefaultBreakpoint(this),
    suspendPolicy = suspendPolicy,
    logMessage = isLogMessage,
    logStack = isLogStack,
    logExpressionObject = logExpressionObject?.toRpc(),
    conditionExpression = conditionExpression?.toRpc(),
  )
}