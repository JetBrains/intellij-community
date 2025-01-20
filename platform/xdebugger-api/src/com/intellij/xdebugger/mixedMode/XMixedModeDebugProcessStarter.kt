// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession

data class XMixedModeProcessesConfiguration(val useLowDebugProcessConsole: Boolean)

data class XMixedModeDebugProcesses(val lowDebugProcess : XDebugProcess, val highDebugProcess : XDebugProcess, val config : XMixedModeProcessesConfiguration)

abstract class XMixedModeDebugProcessStarter {
  abstract fun start(session: XDebugSession): XMixedModeDebugProcesses
}