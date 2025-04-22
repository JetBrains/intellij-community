// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy.Monolith.Companion.getEditorsProvider
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XBreakpointDto(
  val id: XBreakpointId,
  val initialState: XBreakpointDtoState,
  val state: RpcFlow<XBreakpointDtoState>,
  @Transient val localEditorsProvider: XDebuggerEditorsProvider? = null,
  val editorsProviderFileTypeId: String?,
  val typeId: XBreakpointTypeId,
)

@ApiStatus.Internal
@Serializable
data class XBreakpointDtoState(
  val displayText: String,
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
  val shortText: String,
  val isConditionEnabled: Boolean,
  val conditionExpressionInt: XExpressionDto?,
  val generalDescription: String,
  val isLogExpressionEnabled: Boolean,
  val logExpression: String?,
  val logExpressionObjectInt: XExpressionDto?,
  val isTemporary: Boolean,
  val timestamp: Long,
  val currentSessionCustomPresentation: XBreakpointCustomPresentationDto?,
  val customPresentation: XBreakpointCustomPresentationDto?,
)

@ApiStatus.Internal
@Serializable
data class XBreakpointCustomPresentationDto(
  val icon: IconId?,
  val errorMessage: @NlsSafe String?,
  val timestamp: Long,
)

private fun CustomizedBreakpointPresentation.toRpc(): XBreakpointCustomPresentationDto? {
  return XBreakpointCustomPresentationDto(icon?.rpcId(), errorMessage, timestamp)
}

@ApiStatus.Internal
@Serializable
data class XBreakpointTypeIcons(
  val enabledIcon: IconId,
  val disabledIcon: IconId,
  val suspendNoneIcon: IconId,
  val mutedEnabledIcon: IconId,
  val mutedDisabledIcon: IconId,
  val pendingIcon: IconId?,
  val inactiveDependentIcon: IconId,
  val temporaryIcon: IconId?,
)


// TODO: LUXify it for remote dev?
@ApiStatus.Internal
@Serializable
data class XBreakpointTypeCustomPanels(
  @Transient val customPropertiesPanelProvider: (() -> XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?)? = null,
  @Transient val customConditionsPanelProvider: (() -> XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?)? = null,
  @Transient val customRightPropertiesPanelProvider: (() -> XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?)? = null,
  @Transient val customTopPropertiesPanelProvider: (() -> XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?)? = null,
)

@ApiStatus.Internal
@Serializable
enum class XBreakpointTypeSerializableStandardPanels {
  SUSPEND_POLICY, ACTIONS, DEPENDENCY
}

@ApiStatus.Internal
@Serializable
data class XLineBreakpointTypeInfo(
  val priority: Int,
)

@ApiStatus.Internal
suspend fun XBreakpointBase<*, *, *>.toRpc(): XBreakpointDto {
  val editorsProvider = getEditorsProvider(type, this, project)
  return XBreakpointDto(
    id = breakpointId,
    initialState = getDtoState(),
    typeId = XBreakpointTypeId(type.id),
    localEditorsProvider = editorsProvider,
    editorsProviderFileTypeId = editorsProvider?.fileType?.name,
    state = channelFlow {
      breakpointChangedFlow().collectLatest {
        send(getDtoState())
      }
    }.toRpc()
  )
}


private suspend fun XBreakpointBase<*, *, *>.getDtoState(): XBreakpointDtoState {
  val breakpoint = this
  return withContext(Dispatchers.Default) {
    XBreakpointDtoState(
      displayText = XBreakpointUtil.getShortText(breakpoint),
      sourcePosition = sourcePosition?.toRpc(),
      isDefault = XDebuggerManager.getInstance(project).breakpointManager.isDefaultBreakpoint(breakpoint),
      logExpressionObject = logExpressionObject?.toRpc(),
      conditionExpression = conditionExpression?.toRpc(),
      enabled = isEnabled,
      suspendPolicy = suspendPolicy,
      logMessage = isLogMessage,
      logStack = isLogStack,
      userDescription = userDescription,
      group = group,
      shortText = XBreakpointUtil.getShortText(breakpoint),
      isConditionEnabled = isConditionEnabled,
      conditionExpressionInt = conditionExpressionInt?.toRpc(),
      generalDescription = XBreakpointUtil.getGeneralDescription(breakpoint),
      isLogExpressionEnabled = isLogExpressionEnabled,
      logExpression = logExpression,
      logExpressionObjectInt = logExpressionObjectInt?.toRpc(),
      isTemporary = (breakpoint as? XLineBreakpoint<*>)?.isTemporary ?: false,
      timestamp = timeStamp,
      currentSessionCustomPresentation = (XDebuggerManager.getInstance(project).currentSession as? XDebugSessionImpl)?.getBreakpointPresentation(breakpoint)?.toRpc(),
      customPresentation = breakpoint.customizedPresentation?.toRpc()
    )
  }
}