// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.awaitFor

suspend fun refreshVFSAsync() {
  val sessionId = VirtualFileManager.getInstance().asyncRefresh()
  val refreshQueueImpl = RefreshQueue.getInstance() as? RefreshQueueImpl
  val session = refreshQueueImpl?.getSession(sessionId) ?: return
  try {
    session.semaphore.awaitFor()
  }
  catch (t: Throwable) {
    refreshQueueImpl.cancelSession(sessionId)
    throw t
  }
}
