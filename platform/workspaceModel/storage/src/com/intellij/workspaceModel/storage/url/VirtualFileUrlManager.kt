// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url

import com.intellij.util.containers.TreeNodeProcessingResult
import org.jetbrains.annotations.ApiStatus

/**
 * Storage for URLs (in the Virtual File System format) of files that are referenced from workspace model entities. 
 * Use `VirtualFileUrlManager.getInstance(project)` extension function to get instance of this interface inside IDE.
 *
 * [fromUrl] path should be preferred over [fromPath], because the former support files not only from the local file system. 
 * In order to obtain a [VirtualFileUrl] instance for a [VirtualFile][com.intellij.openapi.vfs.VirtualFile], use 
 * `virtualFile.toVirtualFileUrl(virtualFileUrlManager)` extension function.
 */
interface VirtualFileUrlManager {
  companion object

  /**
   * Returns an existing or creates a new instance of [VirtualFileUrl] instance for the given URL in the Virtual File System format.
   */
  fun fromUrl(url: String): VirtualFileUrl

  /**
   * Returns an existing instance of [VirtualFileUrl] for the given URL or `null` if no instance was registered. 
   */
  fun findByUrl(url: String): VirtualFileUrl?

  @ApiStatus.Internal
  fun fromUrlSegments(urls: List<String>): VirtualFileUrl

  /**
   * Returns an existing or creates a new instance of [VirtualFileUrl] instance for the given path to a file in the local filesystems. 
   * It's better to use [fromUrl] wherever possible, because it works for files in other filesystems as well, e.g. inside JAR files and for
   * remove filesystems.
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

