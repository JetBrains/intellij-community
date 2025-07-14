// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import fleet.rpc.core.RpcFlow
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
  val tooltipDescription: String,
  val isLogExpressionEnabled: Boolean,
  val logExpression: String?,
  val logExpressionObjectInt: XExpressionDto?,
  val timestamp: Long,
  val currentSessionCustomPresentation: XBreakpointCustomPresentationDto?,
  val customPresentation: XBreakpointCustomPresentationDto?,
  val lineBreakpointInfo: XLineBreakpointInfo?,
  val requestId: Long,
)

@ApiStatus.Internal
@Serializable
data class XLineBreakpointInfo(
  val isTemporary: Boolean,
  val line: Int,
  val fileUrl: String,
  val highlightingRange: XLineBreakpointTextRange?,
  val file: VirtualFileId?,
)

@ApiStatus.Internal
fun XLineBreakpointTextRange.toTextRange(): TextRange = TextRange(startOffset, endOffset)

@ApiStatus.Internal
fun TextRange.toRpc(): XLineBreakpointTextRange = XLineBreakpointTextRange(startOffset, endOffset)

@ApiStatus.Internal
@Serializable
data class XLineBreakpointTextRange(
  val startOffset: Int,
  val endOffset: Int,
)

@ApiStatus.Internal
@Serializable
data class XBreakpointCustomPresentationDto(
  val icon: IconId?,
  val errorMessage: @NlsSafe String?,
  val timestamp: Long,
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
