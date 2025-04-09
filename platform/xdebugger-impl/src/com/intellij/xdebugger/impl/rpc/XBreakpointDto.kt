// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XBreakpointDto(
  val id: XBreakpointId,
  val displayText: String,
  val iconId: IconId,
  val sourcePosition: XSourcePositionDto?,
  val isDefault: Boolean,
  val logExpressionObject: XExpressionDto?,
  val conditionExpression: XExpressionDto?,
  val initialEnabled: Boolean,
  val initialSuspendPolicy: SuspendPolicy,
  val initialLogMessage: Boolean,
  val initialLogStack: Boolean,
  val initialUserDescription: String?,
  val enabledState: RpcFlow<Boolean>,
  val suspendPolicyState: RpcFlow<SuspendPolicy>,
  val logMessageState: RpcFlow<Boolean>,
  val logStackState: RpcFlow<Boolean>,
  val userDescriptionState: RpcFlow<String?>,
)

@ApiStatus.Internal
suspend fun XBreakpointBase<*, *, *>.toRpc(): XBreakpointDto {
  val breakpointStateBridge = stateBridge

  return XBreakpointDto(
    id = breakpointId,
    displayText = XBreakpointUtil.getShortText(this),
    iconId = getIcon().rpcId().also {
      // let's not cache icon while sending it through RPC
      // TODO: it is better to send all needed icons from BreakpointType
      //  and then collect it on the frontend (taking XBreakpointBase.updateIcon() method)
      clearIcon()
    },
    sourcePosition = sourcePosition?.toRpc(),
    isDefault = XDebuggerManager.getInstance(project).breakpointManager.isDefaultBreakpoint(this),
    logExpressionObject = logExpressionObject?.toRpc(),
    conditionExpression = conditionExpression?.toRpc(),
    initialEnabled = isEnabled,
    initialSuspendPolicy = suspendPolicy,
    initialLogMessage = isLogMessage,
    initialLogStack = isLogStack,
    initialUserDescription = userDescription,
    enabledState = breakpointStateBridge.isEnabledFlow().toRpc(),
    suspendPolicyState = breakpointStateBridge.suspendPolicyFlow().toRpc(),
    logMessageState = breakpointStateBridge.logMessageFlow().toRpc(),
    logStackState = breakpointStateBridge.logStackFlow().toRpc(),
    userDescriptionState = breakpointStateBridge.descriptionFlow().toRpc(),
  )
}