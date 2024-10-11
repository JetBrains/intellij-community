// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

data class XMixedModeDebugProcess(val firstToRun : XDebugProcess, val secondToRun : XDebugProcess)
abstract class XMixedModeDebugProcessStarter {
  abstract fun start(session: XDebugSession): XMixedModeDebugProcess
}