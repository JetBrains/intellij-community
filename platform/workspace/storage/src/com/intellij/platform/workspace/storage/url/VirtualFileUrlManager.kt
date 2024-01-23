// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.url

import com.intellij.util.containers.TreeNodeProcessingResult
import org.jetbrains.annotations.ApiStatus

/**
 * Storage for URLs (in the Virtual File System format) of files that are referenced from workspace model entities. 
 * Use `VirtualFileUrlManager.getInstance(project)` extension function to get instance of this interface inside IDE.
 *
 * In order to obtain a [VirtualFileUrl] instance for a [VirtualFile][com.intellij.openapi.vfs.VirtualFile], use 
 * `virtualFile.toVirtualFileUrl(virtualFileUrlManager)` extension function.
 */
public interface VirtualFileUrlManager {

  /**
   * Returns an existing or creates a new instance of [VirtualFileUrl] instance for the given URL in the Virtual File System format.
   * This function may be used only to obtain an instance which will be stored in a property of a workspace model entity.
   * It must not be used for other purposes (e.g., to convert between different URL formats or to find [VirtualFile][com.intellij.openapi.vfs.VirtualFile],
   * because all created URLs are stored in the shared data structures until the project is closed.
   */
  public fun fromUrl(url: String): VirtualFileUrl

  /**
   * Returns an existing instance of [VirtualFileUrl] for the given URL or `null` if no instance was registered. 
   */
  public fun findByUrl(url: String): VirtualFileUrl?

  @ApiStatus.Internal
  public fun fromUrlSegments(urls: List<String>): VirtualFileUrl

  // This companion object is needed to attach extension methods in the platform:
  //   VirtualFileUrlManager.getInstance(project) and VirtualFileUrlManager.getGlobalInstance()
  public companion object
}
