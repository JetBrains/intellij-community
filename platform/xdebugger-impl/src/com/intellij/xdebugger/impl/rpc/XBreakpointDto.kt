// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
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
  // TODO: let's pass XBreakpointTypeId here and have a single place where all types are registered
  val type: XBreakpointTypeDto,
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
)

@ApiStatus.Internal
@Serializable
data class XBreakpointTypeDto(
  val id: XBreakpointTypeId,
  val index: Int,
  val title: String,
  val suspendThreadSupported: Boolean,
  val lineTypeInfo: XLineBreakpointTypeInfo?,
  val defaultSuspendPolicy: SuspendPolicy,
  val standardPanels: Set<XBreakpointTypeSerializableStandardPanels>,
  val customPanels: XBreakpointTypeCustomPanels,
  val icons: XBreakpointTypeIcons,
)

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

private fun XBreakpointType.StandardPanels.toDto(): XBreakpointTypeSerializableStandardPanels {
  return when (this) {
    XBreakpointType.StandardPanels.SUSPEND_POLICY -> XBreakpointTypeSerializableStandardPanels.SUSPEND_POLICY
    XBreakpointType.StandardPanels.ACTIONS -> XBreakpointTypeSerializableStandardPanels.ACTIONS
    XBreakpointType.StandardPanels.DEPENDENCY -> XBreakpointTypeSerializableStandardPanels.DEPENDENCY
  }
}

@ApiStatus.Internal
fun XBreakpointTypeSerializableStandardPanels.standardPanel(): XBreakpointType.StandardPanels {
  return when (this) {
    XBreakpointTypeSerializableStandardPanels.SUSPEND_POLICY -> XBreakpointType.StandardPanels.SUSPEND_POLICY
    XBreakpointTypeSerializableStandardPanels.ACTIONS -> XBreakpointType.StandardPanels.ACTIONS
    XBreakpointTypeSerializableStandardPanels.DEPENDENCY -> XBreakpointType.StandardPanels.DEPENDENCY
  }
}

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
  val editorsProvider = getEditorsProvider(type, this, project)
  return XBreakpointDto(
    id = breakpointId,
    initialState = getDtoState(),
    type = type.toRpc(project),
    localEditorsProvider = editorsProvider,
    editorsProviderFileTypeId = editorsProvider?.fileType?.name,
    state = channelFlow {
      breakpointChangedFlow().collectLatest {
        send(getDtoState())
      }
    }.toRpc()
  )
}

private fun XBreakpointType<*, *>.toRpc(project: Project): XBreakpointTypeDto {
  val lineTypeInfo = if (this is XLineBreakpointType<*>) {
    XLineBreakpointTypeInfo(priority)
  }
  else {
    null
  }
  val index = XBreakpointType.EXTENSION_POINT_NAME.extensionList.indexOf(this)
  val defaultState = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).getBreakpointDefaults(this)
  val customPanels = XBreakpointTypeCustomPanels(
    customPropertiesPanelProvider = {
      this.createCustomPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
    },
    customConditionsPanelProvider = {
      this.createCustomConditionsPanel() as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
    },
    customRightPropertiesPanelProvider = {
      this.createCustomRightPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
    },
    customTopPropertiesPanelProvider = {
      this.createCustomTopPropertiesPanel(project) as? XBreakpointCustomPropertiesPanel<XBreakpoint<*>>?
    }
  )
  val icons = XBreakpointTypeIcons(
    enabledIcon = enabledIcon.rpcId(),
    disabledIcon = disabledIcon.rpcId(),
    suspendNoneIcon = suspendNoneIcon.rpcId(),
    mutedEnabledIcon = mutedEnabledIcon.rpcId(),
    mutedDisabledIcon = mutedDisabledIcon.rpcId(),
    pendingIcon = pendingIcon?.rpcId(),
    inactiveDependentIcon = inactiveDependentIcon.rpcId(),
    temporaryIcon = (this as? XLineBreakpointType<*>)?.temporaryIcon?.rpcId(),
  )
  // TODO: do we need to subscribe on [defaultState] changes?
  return XBreakpointTypeDto(
    XBreakpointTypeId(id), index, title, isSuspendThreadSupported, lineTypeInfo, defaultState.suspendPolicy,
    standardPanels = visibleStandardPanels.mapTo(mutableSetOf()) { it.toDto() },
    customPanels, icons
  )
}

private suspend fun XBreakpointBase<*, *, *>.getDtoState(): XBreakpointDtoState {
  val breakpoint = this
  return withContext(Dispatchers.EDT) {
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
    )
  }
}