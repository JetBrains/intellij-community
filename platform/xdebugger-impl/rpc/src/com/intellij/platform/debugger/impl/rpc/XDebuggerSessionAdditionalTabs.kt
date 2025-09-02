// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.shared.XDebuggerSessionAdditionalTabId
import kotlinx.serialization.Serializable

@Serializable
sealed interface XDebuggerSessionAdditionalTabEvent {
  // TODO: support tab selected?
  @Serializable
  data class TabAdded(val tabDto: XDebuggerSessionAdditionalTabDto) : XDebuggerSessionAdditionalTabEvent

  @Serializable
  data class TabRemoved(val tabId: XDebuggerSessionAdditionalTabId) : XDebuggerSessionAdditionalTabEvent
}

@Serializable
data class XDebuggerSessionAdditionalTabDto(
  val id: XDebuggerSessionAdditionalTabId,
  val contentId: String,
  val title: @NlsSafe String, val tooltip: String?, val icon: IconId?,
  val closeable: Boolean,
)