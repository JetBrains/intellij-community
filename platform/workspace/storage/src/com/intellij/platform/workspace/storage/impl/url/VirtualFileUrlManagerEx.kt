// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.url

import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.TreeNodeProcessingResult
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
public interface VirtualFileUrlManagerEx : VirtualFileUrlManager {
  /**
   * Returns class of instances produced by this manager, it's used during serialization.
   */
  public val virtualFileUrlImplementationClass: Class<out VirtualFileUrl>

  /**
   * Processes children of [url] and their children recursively using [processor]. [url] itself isn't processed.
   * @return `true` if processing finished normally, or `false` if [processor] returned [STOP][TreeNodeProcessingResult.STOP].
   */
  public fun processChildrenRecursively(url: String, processor: (VirtualFileUrl) -> TreeNodeProcessingResult): Boolean

  /**
   * Returns [VirtualFileUrl] instances which were already created by this manager, without creating new ones.
   */
  public fun getCachedVirtualFileUrls(): List<VirtualFileUrl>
}
