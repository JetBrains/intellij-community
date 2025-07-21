// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.SendChannelSerializer
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XBreakpointTypeApi : RemoteApi<Unit> {
  suspend fun getBreakpointTypeList(project: ProjectId): XBreakpointTypeList

  suspend fun getBreakpointsInfoForLine(projectId: ProjectId, editorId: EditorId, line: Int): XBreakpointsLineInfo

  suspend fun getBreakpointsInfoForEditor(projectId: ProjectId, editorId: EditorId, start: Int, endInclusive: Int): List<XBreakpointsLineInfo>?

  suspend fun addBreakpointThroughLux(projectId: ProjectId, typeId: XBreakpointTypeId): TimeoutSafeResult<XBreakpointDto?>

  suspend fun toggleLineBreakpoint(projectId: ProjectId, request: XLineBreakpointInstallationRequest): XToggleLineBreakpointResponse?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XBreakpointTypeApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XBreakpointTypeApi>())
    }
  }

  suspend fun removeBreakpoint(breakpointId: XBreakpointId)

  suspend fun rememberRemovedBreakpoint(breakpointId: XBreakpointId)
  suspend fun restoreRemovedBreakpoint(projectId: ProjectId)
}

@ApiStatus.Internal
@Serializable
data class XBreakpointsLineInfo(
  val availableTypes: List<XBreakpointTypeId>,
  val singleBreakpointVariant: Boolean,
)

@ApiStatus.Internal
@Serializable
data class XBreakpointTypeList(
  val breakpointTypes: List<XBreakpointTypeDto>,
  val breakpointTypesFlow: RpcFlow<List<XBreakpointTypeDto>>,
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
  val isAddBreakpointButtonVisible: Boolean,
  val icons: XBreakpointTypeIcons,
)

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
sealed interface XToggleLineBreakpointResponse

@ApiStatus.Internal
@Serializable
data class XLineBreakpointInstalledResponse(
  val breakpoint: XBreakpointDto,
) : XToggleLineBreakpointResponse

@ApiStatus.Internal
@Serializable
object XNoBreakpointPossibleResponse : XToggleLineBreakpointResponse

@ApiStatus.Internal
@Serializable
object XRemoveBreakpointResponse : XToggleLineBreakpointResponse

@ApiStatus.Internal
@Serializable
data class XLineBreakpointMultipleVariantResponse(
  val variants: List<XLineBreakpointVariantDto>,
  @Serializable(with = SendChannelSerializer::class) val selectionCallback: SendChannel<VariantSelectedResponse>,
) : XToggleLineBreakpointResponse

@ApiStatus.Internal
@Serializable
data class XLineBreakpointInstallationRequest(
  val types: List<XBreakpointTypeId>,
  val position: XSourcePositionDto,
  val isTemporary: Boolean,
  val isLogging: Boolean,
  val logExpression: String?,
  val hasBreakpoints: Boolean,
)


@ApiStatus.Internal
@Serializable
data class XLineBreakpointVariantDto(
  val text: String,
  val icon: IconId?,
  val highlightRange: XLineBreakpointTextRange?,
  val priority: Int,
  val useAsInline: Boolean,
)

@ApiStatus.Internal
@Serializable
data class VariantSelectedResponse(
  val selectedVariantIndex: Int,
  @Serializable(with = SendChannelSerializer::class) val breakpointCallback: SendChannel<XBreakpointDto>,
)
