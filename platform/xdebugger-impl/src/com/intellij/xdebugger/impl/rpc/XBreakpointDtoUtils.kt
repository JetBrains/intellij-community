// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.readAction
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy.Monolith.Companion.getEditorsProvider
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private fun CustomizedBreakpointPresentation.toRpc(): XBreakpointCustomPresentationDto {
  return XBreakpointCustomPresentationDto(icon?.rpcId(), errorMessage, timestamp)
}

@ApiStatus.Internal
suspend fun XBreakpointBase<*, *, *>.toRpc(): XBreakpointDto {
  val editorsProvider = getEditorsProvider(type, this, project)
  val xDebuggerManager = XDebuggerManager.getInstance(project) as XDebuggerManagerImpl
  return XBreakpointDto(
    id = breakpointId,
    initialState = getDtoState(xDebuggerManager.currentSession),
    typeId = XBreakpointTypeId(type.id),
    localEditorsProvider = editorsProvider,
    editorsProviderFileTypeId = editorsProvider?.fileType?.name,
    state = channelFlow {
      val currentSessionFlow = xDebuggerManager.currentSessionFlow
      breakpointChangedFlow().combine(currentSessionFlow) { _, currentSession ->
        currentSession
      }.collectLatest { currentSession ->
        send(getDtoState(currentSession))
      }
    }.toRpc()
  )
}


private suspend fun XBreakpointBase<*, *, *>.getDtoState(currentSession: XDebugSessionImpl?): XBreakpointDtoState {
  val breakpoint = this
  return withContext(Dispatchers.Default) {
    val manager = XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl
    val completedRequestId = manager.requestCounter.getRequestId()
    XBreakpointDtoState(
      displayText = XBreakpointUtil.getShortText(breakpoint),
      sourcePosition = readAction { sourcePosition?.toRpc() },
      isDefault = manager.isDefaultBreakpoint(breakpoint),
      logMessage = isLogMessage,
      logStack = isLogStack,
      isLogExpressionEnabled = isLogExpressionEnabled,
      logExpression = logExpressionObjectInt?.toRpc(),
      isConditionEnabled = isConditionEnabled,
      conditionExpression = conditionExpressionInt?.toRpc(),
      enabled = isEnabled,
      suspendPolicy = suspendPolicy,
      userDescription = userDescription,
      group = group,
      shortText = XBreakpointUtil.getShortText(breakpoint),
      generalDescription = XBreakpointUtil.getGeneralDescription(breakpoint),
      tooltipDescription = readAction { description },
      timestamp = timeStamp,
      currentSessionCustomPresentation = currentSession?.getBreakpointPresentation(breakpoint)?.toRpc(),
      customPresentation = breakpoint.customizedPresentation?.toRpc(),
      lineBreakpointInfo = readAction { (breakpoint as? XLineBreakpointImpl<*>)?.getInfo() },
      requestId = completedRequestId,
    )
  }
}

private fun XLineBreakpointImpl<*>.getInfo(): XLineBreakpointInfo {
  val highlightingRange = highlightRange?.toRpc()
  return XLineBreakpointInfo(isTemporary, line, fileUrl, highlightingRange, file?.rpcId())
}