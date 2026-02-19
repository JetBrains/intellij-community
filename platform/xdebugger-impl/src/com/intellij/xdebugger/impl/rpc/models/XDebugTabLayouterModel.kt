// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterEvent
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class XDebugTabLayouterModel(
  val layouter: XDebugTabLayouter,
  val ui: RunnerLayoutUi,
  val events: Flow<XDebugTabLayouterEvent>,
)

@ApiStatus.Internal
fun XDebugTabLayouterModel.storeGlobally(cs: CoroutineScope): XDebugTabLayouterId {
  return storeValueGlobally(cs, this, type = XDebugTabLayouterIdType)
}

@ApiStatus.Internal
fun XDebugTabLayouterId.findValue(): XDebugTabLayouterModel? {
  return findValueById(this, type = XDebugTabLayouterIdType)
}

private object XDebugTabLayouterIdType : BackendValueIdType<XDebugTabLayouterId, XDebugTabLayouterModel>(::XDebugTabLayouterId)

