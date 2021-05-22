// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.url

import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.Path

/**
 * Represent an URL (in VFS format) of a file or directory.
 */
open class VirtualFileUrlImpl(val id: Int, internal val manager: VirtualFileUrlManagerImpl): VirtualFileUrl {
  private var cachedUrl: String? = null

  override fun getUrl(): String {
    if (cachedUrl == null) {
      cachedUrl = manager.getUrlById(id)
    }
    return cachedUrl!!
  }

  override fun getFileName(): String = url.substringAfterLast('/')

  override fun getPresentableUrl(): String {
    val calculatedUrl = this.url
    if (calculatedUrl.startsWith("file://")) {
      return calculatedUrl.substring("file://".length)
    }
    else if (calculatedUrl.startsWith("jar://")) {
      val removedSuffix = calculatedUrl.removeSuffix("!/").removeSuffix("!")
      return removedSuffix.substring("jar://".length)
    }
    return calculatedUrl
  }

  override fun getSubTreeFileUrls() = manager.getSubtreeVirtualUrlsById(this)

  override fun append(relativePath: String): VirtualFileUrl = manager.append(this, relativePath.removePrefix("/"))

  override fun toString(): String = this.url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileUrlImpl

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id
}

// TODO It's possible to write it without additional string allocations besides absolutePath

/**
 * Do not use io version in production code as FSD filesystems are incompatible with java.io
 */
@TestOnly
fun File.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = virtualFileManager.fromPath(absolutePath)

fun Path.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = virtualFileManager.fromPath(toAbsolutePath().toString())
