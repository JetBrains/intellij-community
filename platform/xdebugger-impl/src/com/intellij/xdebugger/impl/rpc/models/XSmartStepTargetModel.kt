// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XSmartStepIntoTargetId
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ConsistentCopyVisibility
@ApiStatus.Internal
data class XSmartStepTargetModel internal constructor(
  val coroutineScope: CoroutineScope,
  val target: XSmartStepIntoVariant,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XSmartStepIntoTargetId.findValue(): XSmartStepTargetModel? {
  return findValueById(this, type = XSmartStepTargetType)
}

@ApiStatus.Internal
fun XSmartStepIntoVariant.storeGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XSmartStepIntoTargetId {
  return storeValueGlobally(coroutineScope, XSmartStepTargetModel(coroutineScope, this, session), type = XSmartStepTargetType)
}

private object XSmartStepTargetType : BackendValueIdType<XSmartStepIntoTargetId, XSmartStepTargetModel>(::XSmartStepIntoTargetId)
