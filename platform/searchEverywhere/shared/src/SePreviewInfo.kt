// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
sealed interface SePreviewInfo {
  val fileUrl: VirtualFileId
  val navigationRanges: List<Pair<Int, Int>>
}

@ApiStatus.Experimental
class SePreviewInfoFactory {
  fun create(fileUrl: VirtualFileId, navigationRanges: List<Pair<Int, Int>>): SePreviewInfo =
    SePreviewInfoImpl(fileUrl = fileUrl, navigationRanges = navigationRanges)
}

@ApiStatus.Internal
@Serializable
internal class SePreviewInfoImpl internal constructor(
  override val fileUrl: VirtualFileId,
  override val navigationRanges: List<Pair<Int, Int>>,
) : SePreviewInfo
