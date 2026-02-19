// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlImpl
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class VirtualFileUrlBridge(id: Int, manager: VirtualFileUrlManagerImpl) :
  VirtualFileUrlImpl(id, manager), VirtualFilePointer {

  private val cachedFile: AtomicReference<Pair<VirtualFile?, Long>> = AtomicReference(Pair(null, -1))

  override fun getFile() = findVirtualFile()
  override fun isValid() = findVirtualFile() != null
  override fun toString() = url

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileUrlBridge

    return id == other.id
  }

  override fun hashCode(): Int = id

  private fun findVirtualFile(): VirtualFile? {
    val fileManager = VirtualFileManager.getInstance()
    val cached = cachedFile.get()
    val timestamp = cached.second
    val cachedResults = cached.first
    return if (timestamp == fileManager.modificationCount) cachedResults
    else {
      val modCounterBefore = fileManager.modificationCount
      val file = fileManager.findFileByUrl(url)
      val modCounterAfter = fileManager.modificationCount
      if (modCounterBefore == modCounterAfter) {
        cachedFile.set(Pair(file, modCounterAfter))
      }
      // else {
      // we don't know what we have calculated just now. This might happen, because  findFileByUrl might load (not yet loaded) children
      // and increment the counter, or because the client didn't hold RA and another VFS event has occurred. Either way, don't cache
      // and don't log an error, because incrementing VFS counter from findFileByUrl is expected (though, not desired) behavior.
      // thisLogger().error("Race detected: fileManager.modificationCount has changed during method invocation. Probably, missing ReadAction?")
      // }
      file
    }
  }
}