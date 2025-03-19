// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
enum class AttachViewType(@Nls val displayText: String) {
  LIST(XDebuggerBundle.message("xdebugger.attach.view.list.message")),
  TREE(XDebuggerBundle.message("xdebugger.attach.view.tree.message"))
}