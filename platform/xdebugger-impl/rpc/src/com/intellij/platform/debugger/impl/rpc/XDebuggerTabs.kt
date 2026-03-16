// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.ide.rpc.AnActionId
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Serializable
sealed interface XDebuggerSessionAdditionalTabEvent {
  // TODO: support tab selected?
  @Serializable
  data class TabAdded(val tabDto: XDebuggerSessionAdditionalTabDto) : XDebuggerSessionAdditionalTabEvent

  @Serializable
  data class TabRemoved(val tabId: XDebuggerTabId) : XDebuggerSessionAdditionalTabEvent
}

@Serializable
data class XDebuggerSessionAdditionalTabDto(
  val id: XDebuggerTabId,
  val contentId: String,
  val title: @NlsSafe String, val tooltip: String?, val icon: IconId?,
  val closeable: Boolean,
  val toolbarActionGroupId: AnActionId?,
)

typealias XDebuggerTabId = ComponentDirectTransferId

@ApiStatus.Internal
@Serializable
data class XDebugTabLayouterId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
sealed interface XDebugTabLayouterEvent {
  @Serializable
  data class ContentCreated(
    val contentUniqueId: Int,
    val contentId: String,
    val tabId: XDebuggerTabId,
    val displayName: @Nls String,
    val icon: IconId?,
  ) : XDebugTabLayouterEvent

  @Serializable
  data class TabAdded(val contentUniqueId: Int, val closeable: Boolean) : XDebugTabLayouterEvent

  @Serializable
  data class TabAddedExtended(
    val contentUniqueId: Int,
    val defaultTabId: Int,
    val defaultPlace: PlaceInGrid,
    val defaultIsMinimized: Boolean,
    val closeable: Boolean,
  ) : XDebugTabLayouterEvent

  @Serializable
  data class TabRemoved(val contentUniqueId: Int) : XDebugTabLayouterEvent
}
