// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebuggerSplitModeEnabler {
  companion object {
    val EP_NAME: ExtensionPointName<XDebuggerSplitModeEnabler> = ExtensionPointName.create<XDebuggerSplitModeEnabler>("com.intellij.xdebugger.splitDebuggerModeEnabler")
  }

  fun useSplitDebuggerMode(): Boolean
}