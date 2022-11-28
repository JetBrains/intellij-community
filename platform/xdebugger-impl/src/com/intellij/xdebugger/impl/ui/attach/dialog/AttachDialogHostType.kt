package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.Nls

enum class AttachDialogHostType(@Nls @NlsContexts.Button val displayText: String) {
  LOCAL(XDebuggerBundle.message("xdebugger.local.attach.button.name")),
  REMOTE(XDebuggerBundle.message("xdebugger.remote.attach.button.name"))
}