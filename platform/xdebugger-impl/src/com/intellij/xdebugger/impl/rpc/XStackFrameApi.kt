// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.SimpleTextAttributes
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XStackFrameApi : RemoteApi<Unit> {
  suspend fun customizePresentation(stackFrameId: XStackFrameId): Flow<XStackFramePresentationEvent>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XStackFrameApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XStackFrameApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
sealed interface XStackFramePresentationEvent {
  // TODO: support SimpleTextAttributes serialization
  @ApiStatus.Internal
  @Serializable
  data class AppendTextWithAttributes(
    val fragment: @NlsSafe String,
    @Transient val attributes: SimpleTextAttributes? = null,
  ) : XStackFramePresentationEvent

  @ApiStatus.Internal
  @Serializable
  data class AppendColoredText(
    val fragments: List<TextFragment>,
  ) : XStackFramePresentationEvent

  // TODO: support SimpleTextAttributes serialization
  @ApiStatus.Internal
  @Serializable
  data class TextFragment(
    val text: @NlsSafe String,
    @Transient val attributes: SimpleTextAttributes? = null,
  )

  @ApiStatus.Internal
  @Serializable
  data class SetIcon(
    val iconId: IconId?,
  ) : XStackFramePresentationEvent

  @ApiStatus.Internal
  @Serializable
  data class SetTooltip(
    val text: @NlsSafe String?,
  ) : XStackFramePresentationEvent
}
