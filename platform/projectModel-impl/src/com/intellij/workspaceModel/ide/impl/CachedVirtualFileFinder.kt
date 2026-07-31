// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.concurrent.atomic.AtomicReference

internal class CachedVirtualFileFinder {
  private val cachedFile: AtomicReference<Pair<VirtualFile?, Long>> = AtomicReference(Pair(null, -1))

  fun cacheVirtualFile(file: VirtualFile) {
    cachedFile.set(Pair(file, VirtualFileManager.getInstance().modificationCount))
  }

  fun findVirtualFile(url: String): VirtualFile? {
    val fileManager = VirtualFileManager.getInstance()
    val cached = cachedFile.get()
    val timestamp = cached.second
    val cachedResults = cached.first
    if (timestamp == fileManager.modificationCount) {
      return cachedResults
    }

    val modCounterBefore = fileManager.modificationCount
    val file = fileManager.findFileByUrl(url)
    val modCounterAfter = fileManager.modificationCount
    if (modCounterBefore == modCounterAfter) {
      cachedFile.set(Pair(file, modCounterAfter))
    }
    // we don't know what we have calculated just now. This might happen, because  findFileByUrl might load (not yet loaded) children
    // and increment the counter, or because the client didn't hold RA and another VFS event has occurred. Either way, don't cache
    // and don't log an error, because incrementing VFS counter from findFileByUrl is expected (though, not desired) behavior.
    return file
  }
}
