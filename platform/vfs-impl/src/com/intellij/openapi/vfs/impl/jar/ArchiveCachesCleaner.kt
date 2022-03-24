// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jar

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ArchiveCachesCleaner : PersistentFsConnectionListener {
  private companion object {
    val LOG = logger<ArchiveCachesCleaner>()
  }

  override fun beforeConnectionClosed() {
    for (root in PersistentFS.getInstance().roots) {
      try {
        val fs = root.fileSystem as? ArchiveFileSystem ?: continue
        fs.clearArchiveCache(root)
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }
}