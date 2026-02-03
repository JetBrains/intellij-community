// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.debugger.impl.rpc.XDebuggerEditorsProviderId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XDebuggerEditorsProvider.storeGlobally(cs: CoroutineScope): XDebuggerEditorsProviderId {
  return storeValueGlobally(cs, this, type = XDebuggerEditorsProviderIdType)
}

@ApiStatus.Internal
fun XDebuggerEditorsProviderId.findValue(): XDebuggerEditorsProvider? {
  return findValueById(this, type = XDebuggerEditorsProviderIdType)
}

private object XDebuggerEditorsProviderIdType : BackendValueIdType<XDebuggerEditorsProviderId, XDebuggerEditorsProvider>(::XDebuggerEditorsProviderId)
