// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.debugger.impl.rpc.XDebuggerHyperlinkId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

internal fun XDebuggerTreeNodeHyperlink.storeGlobally(cs: CoroutineScope): XDebuggerHyperlinkId {
  return storeValueGlobally(cs, this, type = XDebuggerHyperlinkIdType)
}

@ApiStatus.Internal
fun XDebuggerHyperlinkId.findValue(): XDebuggerTreeNodeHyperlink? {
  return findValueById(this, type = XDebuggerHyperlinkIdType)
}

private object XDebuggerHyperlinkIdType : BackendValueIdType<XDebuggerHyperlinkId, XDebuggerTreeNodeHyperlink>(::XDebuggerHyperlinkId)
