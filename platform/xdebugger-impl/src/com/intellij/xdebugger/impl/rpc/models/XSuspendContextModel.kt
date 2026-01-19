// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.debugger.impl.rpc.XSuspendContextId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class XSuspendContextModel internal constructor(
  val coroutineScope: CoroutineScope,
  val suspendContext: XSuspendContext,
  val session: XDebugSessionImpl,
) {
  val id: XSuspendContextId = storeValueGlobally(coroutineScope, this, type = XSuspendContextValueIdType)
}

@ApiStatus.Internal
fun XSuspendContextId.findValue(): XSuspendContextModel? {
  return findValueById(this, type = XSuspendContextValueIdType)
}

@ApiStatus.Internal
fun XSuspendContext.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XSuspendContextModel {
  return XSuspendContextDeduplicator.getInstance().getOrCreateModel(coroutineScope, this) {
    XSuspendContextModel(coroutineScope, this, session)
  }
}

@Service(Service.Level.APP)
private class XSuspendContextDeduplicator : ModelDeduplicator<XSuspendContext, XSuspendContextModel>() {
  companion object {
    fun getInstance(): XSuspendContextDeduplicator = service()
  }
}

private object XSuspendContextValueIdType : BackendValueIdType<XSuspendContextId, XSuspendContextModel>(::XSuspendContextId)