// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener

private class IgnoredFileCacheCleaner : PersistentFsConnectionListener {
  override fun beforeConnectionClosed() {
    (ApplicationManager.getApplication()?.serviceIfCreated<FileTypeManager>() as? FileTypeManagerImpl)?.clearIgnoredFileCache()
  }
}
