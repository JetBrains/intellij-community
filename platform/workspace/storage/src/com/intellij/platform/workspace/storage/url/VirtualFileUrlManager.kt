// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.url

import org.jetbrains.annotations.ApiStatus

/**
 * Storage for URLs (in the Virtual File System format) of files that are referenced from workspace model entities. 
 * Use `WorkspaceModel.getVirtualFileUrlManager()` function to get instance of this interface inside IDE.
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
  public fun getOrCreateFromUri(uri: String): VirtualFileUrl

  /**
   * Returns an existing instance of [VirtualFileUrl] for the given URL or `null` if no instance was registered. 
   */
  public fun findByUri(uri: String): VirtualFileUrl?

  /**
   * Returns an existing or creates a new instance of [VirtualFileUrl] instance for the given path to a file in the local filesystems.
   * It's better to use [getOrCreateFromUri] wherever possible, because it works for files in other filesystems as well, e.g. inside JAR files and for
   * remove filesystems.
   */
  @ApiStatus.Obsolete
  public fun fromPath(path: String): VirtualFileUrl
}
