// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDependentBreakpointManagerApi : RemoteApi<Unit> {
  suspend fun breakpointDependencies(projectId: ProjectId): XBreakpointDependenciesDto

  suspend fun clearMasterBreakpoint(breakpointId: XBreakpointId)

  suspend fun setMasterDependency(breakpointId: XBreakpointId, masterBreakpointId: XBreakpointId, isLeaveEnabled: Boolean)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDependentBreakpointManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDependentBreakpointManagerApi>())
    }
  }
}


@ApiStatus.Internal
@Serializable
data class XBreakpointDependenciesDto(
  val initialDependencies: List<XBreakpointDependencyDto>,
  val dependencyEvents: RpcFlow<XBreakpointDependencyEvent>,
)

@ApiStatus.Internal
@Serializable
sealed interface XBreakpointDependencyEvent {
  @Serializable
  data class Add(val dependency: XBreakpointDependencyDto) : XBreakpointDependencyEvent

  @Serializable
  data class Remove(val child: XBreakpointId) : XBreakpointDependencyEvent
}

@ApiStatus.Internal
@Serializable
data class XBreakpointDependencyDto(
  val child: XBreakpointId,
  val parent: XBreakpointId,
  val isLeaveEnabled: Boolean,
)