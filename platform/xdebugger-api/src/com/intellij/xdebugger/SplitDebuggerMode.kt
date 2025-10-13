// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SplitDebuggerMode {
  @JvmStatic
  fun useFeProxy(): Boolean = useFeProxyCachedValue

  const val SPLIT_DEBUGGER_KEY: String = "xdebugger.toolwindow.split"
}

private val useFeProxyCachedValue by lazy {
  Registry.`is`("xdebugger.toolwindow.split") || XDebuggerSplitModeEnabler.EP_NAME.extensionList.any { it.useSplitDebuggerMode() }
}


@ApiStatus.Internal
interface XDebuggerSplitModeEnabler {
  companion object {
    val EP_NAME: ExtensionPointName<XDebuggerSplitModeEnabler> = ExtensionPointName.create("com.intellij.xdebugger.splitDebuggerModeEnabler")
  }

  fun useSplitDebuggerMode(): Boolean
}
