// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.execution.rpc.ExecutionEnvironmentProxyDto
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.ui.icons.IconId
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionAdditionalTabEvent
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.DeferredSerializer
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.SendChannelSerializer
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

// Should be in the RPC module, but it is kept here because it operates with XDebugSessionTab in XDebuggerSessionTabInfoCallback
@ApiStatus.Internal
@Rpc
interface XDebugSessionTabApi : RemoteApi<Unit> {

  suspend fun sessionTabInfo(sessionId: XDebugSessionId): Flow<XDebuggerSessionTabDto>
  suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback)

  suspend fun additionalTabEvents(tabComponentsManagerId: XDebugSessionAdditionalTabComponentManagerId): Flow<XDebuggerSessionAdditionalTabEvent>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebugSessionTabApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebugSessionTabApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabDto(
  val tabInfo: XDebuggerSessionTabAbstractInfo,
  val pausedInfo: RpcFlow<XDebugSessionPausedInfo?>,
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionPausedInfo(
  val pausedByUser: Boolean,
  val topFrameIsAbsent: Boolean,
)

@ApiStatus.Internal
@Serializable
sealed interface XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
object XDebuggerSessionTabInfoNoInit : XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabInfoCallback(
  @Transient val tab: XDebugSessionTab? = null,
)

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabInfo(
  val iconId: IconId?,
  val forceNewDebuggerUi: Boolean,
  val withFramesCustomization: Boolean,
  val defaultFramesViewKey: String?,
  // TODO pass to frontend
  @Transient val contentToReuse: RunContentDescriptor? = null,
  val executionEnvironmentId: ExecutionEnvironmentId?,
  val executionEnvironmentProxyDto: ExecutionEnvironmentProxyDto?,
  val additionalTabsComponentManagerId: XDebugSessionAdditionalTabComponentManagerId,
  @Serializable(with = SendChannelSerializer::class) val tabClosedCallback: SendChannel<Unit>,
  @Serializable(with = DeferredSerializer::class) val backendRunContendDescriptorId: Deferred<RunContentDescriptorId>,
) : XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
data class ExecutionEnvironmentId(override val uid: UID) : Id


@ApiStatus.Internal
@Serializable
data class RunContentDescriptorId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XDebugSessionAdditionalTabComponentManagerId(override val uid: UID) : Id