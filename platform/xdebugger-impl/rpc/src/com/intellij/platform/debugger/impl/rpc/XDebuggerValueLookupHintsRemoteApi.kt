// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import com.intellij.platform.rpc.topics.RemoteTopic
import com.intellij.platform.rpc.topics.ApplicationRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerValueLookupHintsRemoteApi : RemoteApi<Unit> {
  suspend fun adjustOffset(projectId: ProjectId, editorId: EditorId, offset: Int): Int

  suspend fun getExpressionInfo(projectId: ProjectId, editorId: EditorId, offset: Int, hintType: ValueHintType): ExpressionInfo?

  suspend fun createHint(projectId: ProjectId, editorId: EditorId, offset: Int, hintType: ValueHintType): RemoteValueHintId?

  suspend fun showHint(hintId: RemoteValueHintId): Flow<Unit>

  suspend fun removeHint(hintId: RemoteValueHintId, force: Boolean)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerValueLookupHintsRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class RemoteValueHintId(override val uid: UID) : Id

@ApiStatus.Internal
val LOOKUP_HINTS_EVENTS_REMOTE_TOPIC: ProjectRemoteTopic<ValueHintEvent> = ProjectRemoteTopic("xdebugger.lookup.hints.events", ValueHintEvent.serializer())

@ApiStatus.Internal
@Serializable
sealed interface ValueHintEvent {
  @Serializable
  data object StartListening : ValueHintEvent

  @Serializable
  data object HideHint : ValueHintEvent
}