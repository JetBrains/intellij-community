// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jar

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener

private class ArchiveCachesCleaner : PersistentFsConnectionListener {
  override fun beforeConnectionClosed() {
    for (root in (ManagingFS.getInstanceOrNull() ?: return).roots) {
      val fs = root.fileSystem
      if (fs is ArchiveFileSystem && fs !is Disposable) {
        try {
          fs.clearArchiveCache(root)
        }
        catch (e: Exception) {
          logger<ArchiveCachesCleaner>().error(e)
        }
      }
    }
  }
}
