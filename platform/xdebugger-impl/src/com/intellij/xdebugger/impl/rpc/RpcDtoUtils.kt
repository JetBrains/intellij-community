// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.rpcId
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeNodeHyperlinkDto
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import kotlinx.coroutines.CoroutineScope

fun XDebuggerTreeNodeHyperlink.toRpc(cs: CoroutineScope): XDebuggerTreeNodeHyperlinkDto {
  val id = storeGlobally(cs)
  return XDebuggerTreeNodeHyperlinkDto(
    id,
    linkText,
    linkTooltip,
    linkIcon?.rpcId(),
    shortcutSupplier?.get(),
    alwaysOnScreen(),
    textAttributes,
    this,
  )
}
