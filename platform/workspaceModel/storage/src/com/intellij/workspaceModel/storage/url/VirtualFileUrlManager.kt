// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url

/**
 * A storage for URLs (in VFS format) of files which are referenced from workspace model entities.
 */
interface VirtualFileUrlManager {
  companion object
  fun fromUrl(url: String): VirtualFileUrl
  fun fromPath(path: String): VirtualFileUrl
  fun getSubtreeVirtualUrlsById(vfu: VirtualFileUrl): List<VirtualFileUrl>
  fun getParentVirtualUrl(vfu: VirtualFileUrl): VirtualFileUrl?
}

