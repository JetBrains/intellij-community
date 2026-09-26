// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.debugger.impl.rpc.XDebugSessionDataId
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XDebugSessionId.findValue(): XDebugSessionImpl? {
  return findValueById(this, type = XDebugSessionValueIdType)
}

internal fun XDebugSessionImpl.storeGlobally(cs: CoroutineScope): XDebugSessionId {
  return storeValueGlobally(cs, this, type = XDebugSessionValueIdType)
}

private object XDebugSessionValueIdType : BackendValueIdType<XDebugSessionId, XDebugSessionImpl>(::XDebugSessionId)

@ApiStatus.Internal
data class SessionDataModel(
  val sessionData: XDebugSessionData,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XDebugSessionDataId.findValue(): SessionDataModel? {
  return findValueById(this, type = XDebugSessionDataValueIdType)
}

internal fun XDebugSessionData.storeGlobally(cs: CoroutineScope, session: XDebugSessionImpl): XDebugSessionDataId {
  return storeValueGlobally(cs, SessionDataModel(this, session), type = XDebugSessionDataValueIdType)
}

private object XDebugSessionDataValueIdType : BackendValueIdType<XDebugSessionDataId, SessionDataModel>(::XDebugSessionDataId)
