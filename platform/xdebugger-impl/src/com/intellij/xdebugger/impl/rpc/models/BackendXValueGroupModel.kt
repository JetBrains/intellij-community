// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XValueGroup
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XValueGroupId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendXValueGroupModel internal constructor(
  val cs: CoroutineScope,
  val session: XDebugSessionImpl,
  val xValueGroup: XValueGroup,
) {
  val id: XValueGroupId = storeValueGlobally(cs, this, type = BackendXValueGroupIdType)
}

private object BackendXValueGroupIdType : BackendValueIdType<XValueGroupId, BackendXValueGroupModel>(::XValueGroupId)

@ApiStatus.Internal
fun XValueGroupId.findValue(): BackendXValueGroupModel? {
  return findValueById(this, type = BackendXValueGroupIdType)
}

@ApiStatus.Internal
fun XValueGroup.getOrStoreGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): BackendXValueGroupModel {
  return XValueGroupDeduplicator.getInstance().getOrCreateModel(coroutineScope, this) {
    BackendXValueGroupModel(coroutineScope, session, this)
  }
}


@Service(Service.Level.APP)
private class XValueGroupDeduplicator : ModelDeduplicator<XValueGroup, BackendXValueGroupModel>() {
  companion object {
    @JvmStatic
    fun getInstance(): XValueGroupDeduplicator = service()
  }
}
