// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.idea.AppMode
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SplitDebuggerMode {
  @JvmStatic
  fun isSplitDebugger(): Boolean = isSplitDebuggerCachedValue

  @JvmStatic
  fun showSplitWarnings(): Boolean = isSplitDebugger() && Registry.`is`("xdebugger.toolwindow.split.warnings")

  const val SPLIT_DEBUGGER_KEY: String = "xdebugger.toolwindow.split"

  fun setEnabled(enabled: Boolean) {
    Registry.get(SPLIT_DEBUGGER_KEY).setValue(enabled)

    val remDevKey = "xdebugger.toolwindow.split.remdev"
    // disable the remdev key if it is enabled
    if (Registry.`is`(remDevKey, false) && !enabled) {
      Registry.get(remDevKey).setValue(false)
    }
  }
}

private val isSplitDebuggerCachedValue by lazy {
  Registry.`is`("xdebugger.toolwindow.split") || isSplitDebuggerInRemDevCachedValue
}

private val isSplitDebuggerInRemDevCachedValue by lazy {
  // We do not care about CWM anymore that this is isBackend or isFrontend clause
  (AppMode.isRemoteDevHost() || PlatformUtils.isJetBrainsClient()) &&
  Registry.`is`("xdebugger.toolwindow.split.remdev", false)
}
