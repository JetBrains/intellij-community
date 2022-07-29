package com.intellij.xdebugger.impl.ui.attach.dialog.extensions

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.attach.XAttachDebugger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
interface XAttachDebuggerButtonPresentationProvider {
  @Nls
  fun getCustomActionPresentation(): String
}

fun XAttachDebugger?.getActionPresentation(): String {
  if (this == null) return XDebuggerBundle.message("xdebugger.attach.button.no.debugger.name")
  if (this is XAttachDebuggerButtonPresentationProvider) return getCustomActionPresentation()
  return XDebuggerBundle.message("xdebugger.attach.button.name", debuggerDisplayName)
}