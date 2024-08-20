// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.util.UID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerValueLookupHintsRemoteApi : RemoteApi<Unit> {
  suspend fun canShowHint(projectId: UID, editorId: EditorId, offset: Int, hintType: ValueHintType): Boolean

  suspend fun createHint(projectId: UID, editorId: EditorId, offset: Int, hintType: ValueHintType): RemoteValueHint?

  suspend fun showHint(projectId: UID, hintId: Int): Flow<Unit>

  suspend fun removeHint(projectId: UID, hintId: Int)
}

@ApiStatus.Internal
@Serializable
data class RemoteValueHint(val id: Int)