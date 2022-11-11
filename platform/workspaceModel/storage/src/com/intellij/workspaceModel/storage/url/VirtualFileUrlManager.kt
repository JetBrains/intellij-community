// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url

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
  fun fromUrl(url: String): VirtualFileUrl
  @ApiStatus.Internal
  fun fromUrlSegments(urls: List<String>): VirtualFileUrl
  /**
   * Method should be used with care. Please, see [class][VirtualFileUrlManager] kdoc for details.
   */
  fun fromPath(path: String): VirtualFileUrl
  fun getSubtreeVirtualUrlsById(vfu: VirtualFileUrl): List<VirtualFileUrl>
  fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl?
}

