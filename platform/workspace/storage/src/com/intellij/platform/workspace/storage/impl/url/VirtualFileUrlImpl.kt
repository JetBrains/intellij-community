// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl.url

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.Path

/**
 * Represent a URL (in VFS format) of a file or directory.
 */
public open class VirtualFileUrlImpl(public val id: Int, internal val manager: VirtualFileUrlManagerImpl): VirtualFileUrl {
  private var cachedUrl: String? = null

  override fun getUrl(): String {
    if (cachedUrl == null) {
      cachedUrl = manager.getUrlById(id)
    }
    return cachedUrl!!
  }

  public fun getUrlSegments(): List<String> {
    return url.split('/', '\\')
  }

  override fun getFileName(): String = url.substringAfterLast('/')

  override fun getParent(): VirtualFileUrl? = manager.getParentVirtualUrl(this)

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

  override fun getSubTreeFileUrls(): List<VirtualFileUrl> = manager.getSubtreeVirtualUrlsById(this)

  override fun append(relativePath: String): VirtualFileUrl = manager.append(this, relativePath.removePrefix("/"))

  override fun toString(): String = this.url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileUrlImpl

    return id == other.id
  }

  override fun hashCode(): Int = id
}

// TODO It's possible to write it without additional string allocations besides absolutePath

/**
 * Do not use io version in production code as FSD filesystems are incompatible with java.io
 */
@TestOnly
public fun File.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = absolutePath.toVirtualFileUrl(virtualFileManager)

public fun Path.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = toAbsolutePath().toString().toVirtualFileUrl(virtualFileManager)

public fun String.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl {
  val url = URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtil.toSystemIndependentName(this)
  return virtualFileManager.fromUrl(url)
}
