// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url

import com.intellij.util.containers.TreeNodeProcessingResult
import org.jetbrains.annotations.ApiStatus

/**
 * Storage for URLs (in VFS format) of files that are referenced from workspace model entities.
 *
 * It's quite common to construct [VirtualFileUrl] instance from [com.intellij.openapi.vfs.VirtualFile]. However, this should be made with
 * care. Though [fromPath] and [fromUrl] look similar, they should be used with care. The result might differ depending on the underlying
 * protocol of the [com.intellij.openapi.vfs.VirtualFile] instance. Here is an example.
 * ```
 * val file: VirtualFile = ...
 * val urlManager: VirtualFileUrlManager = ...
 *
 * // Protocol prefix is preserved. Safe way.
 * val fromUrl = urlManager.fromUrl(file.url)
 *
 * // Beware of using this approach for .jar files, for example.
 * // Resulting URL might get protocol prefix different from the initial one, e.g. 'jar://' => 'file://'.
 * val fromPath = urlManager.fromPath(file.path)
 *
 * check(fromUrl.virtualFile != null)
 * check(fromPath.virtualFile != null) { "Might be null" }
 * ```
 *
 */
interface VirtualFileUrlManager {
  companion object

  /**
   * Returns existing or creates a new instance of [VirtualFileUrl] instance for the given URL in the Virtual File System format.
   */
  fun fromUrl(url: String): VirtualFileUrl

  /**
   * Returns an existing instance of [VirtualFileUrl] for the given URL or `null` if no instance was registered. 
   */
  fun findByUrl(url: String): VirtualFileUrl?

  @ApiStatus.Internal
  fun fromUrlSegments(urls: List<String>): VirtualFileUrl
  /**
   * Method should be used with care. Please, see [class][VirtualFileUrlManager] kdoc for details.
   */
  fun fromPath(path: String): VirtualFileUrl
  fun getSubtreeVirtualUrlsById(vfu: VirtualFileUrl): List<VirtualFileUrl>

  /**
   * Processes children of [url] and their children recursively using [processor]. [url] itself isn't processed.
   * @return `true` if processing finished normally, or `false` if [processor] returned [STOP][TreeNodeProcessingResult.STOP].
   */
  fun processChildrenRecursively(url: VirtualFileUrl, processor: (VirtualFileUrl) -> TreeNodeProcessingResult): Boolean
  
  fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl?
}

