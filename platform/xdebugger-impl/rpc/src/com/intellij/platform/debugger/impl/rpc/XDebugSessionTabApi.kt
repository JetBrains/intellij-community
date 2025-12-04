// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.rpc.ExecutionEnvironmentProxyDto
import com.intellij.ide.ui.icons.IconId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import com.intellij.xdebugger.ui.IXDebuggerSessionTab
import com.intellij.xdebugger.ui.XDebugTabLayouter
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

@ApiStatus.Internal
@Rpc
interface XDebugSessionTabApi : RemoteApi<Unit> {

  suspend fun sessionTabInfo(sessionDataId: XDebugSessionDataId): Flow<XDebuggerSessionTabDto>
  suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback)

  suspend fun additionalTabEvents(tabComponentsManagerId: XDebugSessionAdditionalTabComponentManagerId): Flow<XDebuggerSessionAdditionalTabEvent>
  suspend fun tabLayouterEvents(tabLayouterId: XDebugTabLayouterId): Flow<XDebugTabLayouterEvent>

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
  val pausedInfo: RpcFlow<XDebugSessionPausedInfo>,
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
  @Transient val tab: IXDebuggerSessionTab? = null,
)

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabInfo(
  val iconId: IconId?,
  val forceNewDebuggerUi: Boolean,
  val withFramesCustomization: Boolean,
  val defaultFramesViewKey: String?,
  val executionEnvironmentId: ExecutionEnvironmentId?,
  val executionEnvironmentProxyDto: ExecutionEnvironmentProxyDto?,
  val additionalTabsComponentManagerId: XDebugSessionAdditionalTabComponentManagerId,
  @Serializable(with = SendChannelSerializer::class) val tabClosedCallback: SendChannel<Unit>,
  @Serializable(with = DeferredSerializer::class) val backendRunContendDescriptorId: Deferred<RunContentDescriptorIdImpl>,
  @Serializable(with = DeferredSerializer::class) val showTab: Deferred<Unit>,
  @Serializable(with = DeferredSerializer::class) val tabLayouterDto: Deferred<XDebugTabLayouterDto>,
) : XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
data class XDebugTabLayouterDto(
  val id: XDebugTabLayouterId,
  @Transient val localLayouter: XDebugTabLayouter? = null,
)

@ApiStatus.Internal
@Serializable
data class ExecutionEnvironmentId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XDebugSessionAdditionalTabComponentManagerId(override val uid: UID) : Id