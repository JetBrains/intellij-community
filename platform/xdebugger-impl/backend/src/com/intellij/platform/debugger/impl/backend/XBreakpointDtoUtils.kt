// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.util.toRpc
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.readAction
import com.intellij.platform.debugger.impl.rpc.XBreakpointCustomPresentationDto
import com.intellij.platform.debugger.impl.rpc.XBreakpointDto
import com.intellij.platform.debugger.impl.rpc.XBreakpointDtoState
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeId
import com.intellij.platform.debugger.impl.rpc.XLineBreakpointInfo
import com.intellij.platform.debugger.impl.rpc.toRpc
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.impl.breakpoints.highlightRange
import com.intellij.xdebugger.impl.proxy.getEditorsProvider
import com.intellij.xdebugger.impl.rpc.toRpc
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

internal fun CustomizedBreakpointPresentation.toRpc(): XBreakpointCustomPresentationDto {
  return XBreakpointCustomPresentationDto(icon?.rpcId(), errorMessage, timestamp)
}

internal suspend fun XBreakpointBase<*, *, *>.toRpc(): XBreakpointDto {
  val editorsProvider = getEditorsProvider(type, this, project)
  val xDebuggerManager = XDebuggerManager.getInstance(project) as XDebuggerManagerImpl
  val currentSession = xDebuggerManager.currentSession

  return XBreakpointDto(
    id = breakpointId,
    initialState = getDtoState(),
    initialCustomPresentation = customizedPresentation?.toRpc(),
    initialCurrentSessionCustomPresentation = currentSession?.getBreakpointPresentation(this)?.toRpc(),
    typeId = XBreakpointTypeId(type.id),
    editorsProviderDto = editorsProvider?.toRpc(coroutineScope),
    state = channelFlow {
      val currentSessionFlow = xDebuggerManager.currentSessionFlow
      // XBreakpointBase#getDescription depends on the current session
      // BreakpointWithHighlighter#getPropertyXMLDescriptions
      breakpointChangedFlow().combine(currentSessionFlow) { _, _ -> }.collectLatest {
        send(getDtoState())
      }
    }.toRpc()
  )
}


private suspend fun XBreakpointBase<*, *, *>.getDtoState(): XBreakpointDtoState {
  val breakpoint = this
  return withContext(Dispatchers.Default) {
    val manager = XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl
    val completedRequestId = manager.requestCounter.getRequestId(breakpoint.breakpointId)
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
      lineBreakpointInfo = readAction { (breakpoint as? XLineBreakpointImpl<*>)?.getInfo() },
      requestId = completedRequestId,
    )
  }
}

private fun XLineBreakpointImpl<*>.getInfo(): XLineBreakpointInfo {
  val range = highlightRange
  val highlightingRange = range?.toRpc()
  return XLineBreakpointInfo(isTemporary, line, fileUrl, highlightingRange, file?.rpcId())
}