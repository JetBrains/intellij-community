package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.Nls

enum class AttachViewType(@Nls val displayText: String) {
  LIST(XDebuggerBundle.message("xdebugger.attach.view.list.message")),
  TREE(XDebuggerBundle.message("xdebugger.attach.view.tree.message"))
}