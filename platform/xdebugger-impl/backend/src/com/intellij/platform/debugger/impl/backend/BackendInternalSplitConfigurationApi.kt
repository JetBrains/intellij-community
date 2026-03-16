// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.debugger.impl.rpc.InternalSplitConfigurationApi
import com.intellij.xdebugger.SplitDebuggerMode

internal class BackendInternalSplitConfigurationApi : InternalSplitConfigurationApi {
  override suspend fun isSplitDebuggersEnabled(): Boolean {
    return SplitDebuggerMode.isSplitDebugger()
  }

  override suspend fun setSplitDebuggersEnabled(enabled: Boolean) {
    SplitDebuggerMode.setEnabled(enabled)
  }
}