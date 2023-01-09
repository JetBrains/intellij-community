package com.intellij.xdebugger.impl.ui

import com.intellij.xdebugger.XDebugProcess
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
interface XDebugSessionTabCustomizer {
  fun getBottomLocalsComponentProvider(): SessionTabComponentProvider? = null

  fun allowFramesViewCustomization(): Boolean = false
}

interface SessionTabComponentProvider {
  fun createBottomLocalsComponent(): JComponent
}

fun XDebugProcess.allowFramesViewCustomization(): Boolean {
  return (session.debugProcess as? XDebugSessionTabCustomizer)?.allowFramesViewCustomization() ?: false
}

fun XDebugProcess.getBottomLocalsComponentProvider(): SessionTabComponentProvider? {
  return (session.debugProcess as? XDebugSessionTabCustomizer)?.getBottomLocalsComponentProvider()
}