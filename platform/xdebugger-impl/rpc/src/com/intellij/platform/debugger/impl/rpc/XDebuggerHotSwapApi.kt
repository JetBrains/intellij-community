// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerHotSwapApi : RemoteApi<Unit> {
  suspend fun currentSessionStatus(projectId: ProjectId): Flow<XDebugHotSwapCurrentSessionStatus?>
  suspend fun performHotSwap(sessionId: XDebugHotSwapSessionId, source: HotSwapSource)
  suspend fun hide(projectId: ProjectId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerHotSwapApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerHotSwapApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class XDebugHotSwapSessionId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XDebugHotSwapCurrentSessionStatus(val sessionId: XDebugHotSwapSessionId, val status: HotSwapVisibleStatus)

@ApiStatus.Internal
enum class HotSwapVisibleStatus {
  NO_CHANGES, CHANGES_READY, IN_PROGRESS, SUCCESS, HIDDEN
}

@ApiStatus.Internal
@Serializable
enum class HotSwapSource {
  RELOAD_FILE,
  RELOAD_ALL,
  ON_REBUILD_AUTO,
  ON_REBUILD_ASK,
  RELOAD_MODIFIED_ACTION,
  RELOAD_MODIFIED_BUTTON,
}
