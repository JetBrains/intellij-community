package com.intellij.xdebugger.impl.ui

import com.intellij.xdebugger.XDebugProcess
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
interface XDebugSessionTabCustomizer {
  fun getBottomLocalsComponentProvider(): SessionTabComponentProvider? = null

  fun allowFramesViewCustomization(): Boolean = false

  fun getDefaultFramesViewKey(): String? = null

  fun forceShowNewDebuggerUi(): Boolean = false
}

interface SessionTabComponentProvider {
  fun createBottomLocalsComponent(): JComponent
}

fun XDebugProcess.allowFramesViewCustomization(): Boolean {
  return (this as? XDebugSessionTabCustomizer)?.allowFramesViewCustomization() ?: false
}

fun XDebugProcess.getBottomLocalsComponentProvider(): SessionTabComponentProvider? {
  return (this as? XDebugSessionTabCustomizer)?.getBottomLocalsComponentProvider()
}

@ApiStatus.Internal
fun XDebugProcess.useSplitterView(): Boolean = getBottomLocalsComponentProvider() != null


fun XDebugProcess.forceShowNewDebuggerUi(): Boolean {
  return (this as? XDebugSessionTabCustomizer)?.forceShowNewDebuggerUi() ?: false
}