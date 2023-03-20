package com.intellij.xdebugger.impl.ui.attach.dialog.extensions

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Experimental
interface XAttachTreeDebuggersPresentationProvider {

  @Nls
  fun getDebuggersShortName(): String
}