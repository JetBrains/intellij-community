// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.debugger.impl.rpc.XSuspendContextId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Wraps [XSuspendContext] with a [coroutineScope] that is canceled when the session resumes.
 *
 * Use [XSuspendContextId.findValue] to retrieve the model by its [id].
 */
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

private object XSuspendContextValueIdType : BackendValueIdType<XSuspendContextId, XSuspendContextModel>(::XSuspendContextId)