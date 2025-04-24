// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpointType
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XBreakpointTypeApi : RemoteApi<Unit> {
  suspend fun getBreakpointTypeList(project: ProjectId): XBreakpointTypeList

  suspend fun getAvailableBreakpointTypesForLine(projectId: ProjectId, editorId: EditorId, line: Int): List<XBreakpointTypeId>

  suspend fun getAvailableBreakpointTypesForEditor(projectId: ProjectId, editorId: EditorId, start: Int, endInclusive: Int): List<List<XBreakpointTypeId>>?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XBreakpointTypeApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XBreakpointTypeApi>())
    }
  }
}

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
  val customPanels: XBreakpointTypeCustomPanels,
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
data class XBreakpointTypeId(val id: String)