// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XBreakpointDto(
  val id: XBreakpointId,
  val initialState: XBreakpointDtoState,
  val state: RpcFlow<XBreakpointDtoState>,
  // TODO: let's pass XBreakpointTypeId here and have a single place where all types are registered
  val type: XBreakpointTypeDto,
)

@ApiStatus.Internal
@Serializable
data class XBreakpointDtoState(
  val displayText: String,
  val iconId: IconId,
  val sourcePosition: XSourcePositionDto?,
  val isDefault: Boolean,
  val logExpressionObject: XExpressionDto?,
  val conditionExpression: XExpressionDto?,
  val enabled: Boolean,
  val suspendPolicy: SuspendPolicy,
  val logMessage: Boolean,
  val logStack: Boolean,
  val userDescription: String?,
  val group: String?,
)

@ApiStatus.Internal
@Serializable
data class XBreakpointTypeDto(
  val id: XBreakpointTypeId,
  val index: Int,
  val title: String,
  val enabledIcon: IconId,
  val suspendThreadSupported: Boolean,
  val lineTypeInfo: XLineBreakpointTypeInfo?,
)

@ApiStatus.Internal
@Serializable
data class XBreakpointTypeId(val id: String)

@ApiStatus.Internal
@Serializable
data class XLineBreakpointTypeInfo(
  val priority: Int,
)

@ApiStatus.Internal
suspend fun XBreakpointBase<*, *, *>.toRpc(): XBreakpointDto {
  return XBreakpointDto(
    id = breakpointId,
    initialState = getDtoState(),
    type = type.toRpc(),
    state = channelFlow {
      breakpointChangedFlow().collectLatest {
        send(getDtoState())
      }
    }.toRpc()
  )
}

private fun XBreakpointType<*, *>.toRpc(): XBreakpointTypeDto {
  val lineTypeInfo = if (this is XLineBreakpointType<*>) {
    XLineBreakpointTypeInfo(priority)
  }
  else {
    null
  }
  val index = XBreakpointType.EXTENSION_POINT_NAME.extensionList.indexOf(this)
  return XBreakpointTypeDto(XBreakpointTypeId(id), index, title, enabledIcon.rpcId(), isSuspendThreadSupported, lineTypeInfo)
}

private suspend fun XBreakpointBase<*, *, *>.getDtoState(): XBreakpointDtoState {
  return withContext(Dispatchers.EDT) {
    XBreakpointDtoState(
      displayText = XBreakpointUtil.getShortText(this@getDtoState),
      iconId = getIcon().rpcId().also {
        // let's not cache icon while sending it through RPC
        // TODO: it is better to send all needed icons from BreakpointType
        //  and then collect it on the frontend (taking XBreakpointBase.updateIcon() method)
        clearIcon()
      },
      sourcePosition = sourcePosition?.toRpc(),
      isDefault = XDebuggerManager.getInstance(project).breakpointManager.isDefaultBreakpoint(this@getDtoState),
      logExpressionObject = logExpressionObject?.toRpc(),
      conditionExpression = conditionExpression?.toRpc(),
      enabled = isEnabled,
      suspendPolicy = suspendPolicy,
      logMessage = isLogMessage,
      logStack = isLogStack,
      userDescription = userDescription,
      group = group,
    )
  }
}