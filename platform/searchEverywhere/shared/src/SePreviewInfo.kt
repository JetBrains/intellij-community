// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents preview information for a given file in the Search Everywhere context.
 *
 * @property fileUrl The virtual file identifier associated with the preview.
 * @property navigationRanges A list of pairs specifying ranges in the file
 */
@ApiStatus.Experimental
@Serializable
sealed interface SePreviewInfo {
  val fileUrl: VirtualFileId
  val navigationRanges: List<Pair<Int, Int>>
}

/**
 * Factory class for creating instances of `SePreviewInfo`.
 */
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
