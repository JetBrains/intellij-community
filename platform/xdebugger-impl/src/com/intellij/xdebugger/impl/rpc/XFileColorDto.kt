// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XFileColorDto(
  val virtualFileId: VirtualFileId,
  val colorState: SerializedColorState,
)

@ApiStatus.Internal
@Serializable
sealed interface SerializedColorState {
  val colorId: ColorId?

  @Serializable
  object NoColor : SerializedColorState {
    override val colorId: ColorId?
      get() = null
  }

  @Serializable
  object Computing : SerializedColorState {
    override val colorId: ColorId?
      get() = null
  }

  @Serializable
  data class Computed(override val colorId: ColorId) : SerializedColorState
}
