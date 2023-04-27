// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.*

suspend fun refreshVFSAsync() {
  val sessionId = VirtualFileManager.getInstance().asyncRefresh()
  val refreshQueueImpl = RefreshQueue.getInstance() as? RefreshQueueImpl
  val session = refreshQueueImpl?.getSession(sessionId) ?: return
  try {
    while (!wait(session)) {
      yield()
    }
  }
  catch (t: Throwable) {
    refreshQueueImpl.cancelSession(sessionId)
    throw t
  }
}

private suspend fun wait(session: RefreshSessionImpl) = runInterruptible(Dispatchers.IO) {
  session.waitFor(100L)
}