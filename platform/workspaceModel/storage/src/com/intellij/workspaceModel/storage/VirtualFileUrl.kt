// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import java.io.File
import java.nio.file.Path

// TODO Do we want to make it inline?
class VirtualFileUrl(private val id: Int, internal val manager: VirtualFileUrlManager) {
  val url: String
    get() = manager.getUrlById(id)

  val parent: VirtualFileUrl?
    get() = manager.getParentVirtualUrlById(id)

  val file: File?
    get() = filePath?.let { File(it) }

  val filePath: String?
    get() {
      val calculatedUrl = url

      if (calculatedUrl.isEmpty()) return null

      if (calculatedUrl.startsWith("file://")) {
        return calculatedUrl.substring("file://".length)
      }
      else if (calculatedUrl.startsWith("jar://")) {
        val removedSuffix = calculatedUrl.removeSuffix("!/").removeSuffix("!")
        if (removedSuffix.contains('!')) return null

        return removedSuffix.substring("jar://".length)
      }

      return null
    }

  fun isEqualOrParentOf(other: VirtualFileUrl): Boolean = manager.isEqualOrParentOf(this.id, other.id)

  //override fun equals(other: Any?): Boolean = id == (other as? VirtualFileUrl)?.id
  //override fun hashCode(): Int = id
  override fun toString(): String = url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileUrl

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int = id
}

fun VirtualFileUrl.append(relativePath: String): VirtualFileUrl {
  return manager.fromUrl(url + "/" + relativePath.removePrefix("/"))
}

// TODO It's possible to write it without additional string allocations besides absolutePath
fun File.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = virtualFileManager.fromPath(absolutePath)

fun Path.toVirtualFileUrl(virtualFileManager: VirtualFileUrlManager): VirtualFileUrl = virtualFileManager.fromPath(toAbsolutePath().toString())
