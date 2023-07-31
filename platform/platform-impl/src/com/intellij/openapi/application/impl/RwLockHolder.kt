// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class RwLockHolder(writeThread: Thread) {
  @JvmField
  internal val lock: ReadMostlyRWLock = ReadMostlyRWLock(writeThread)

  // @Throws(E::class)
  fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T {
    val wilock = acquireWriteIntentLock(computation.javaClass.getName())
    try {
      return computation.compute()
    }
    finally {
      if (wilock) {
        lock.writeIntentUnlock()
      }
    }
  }

  fun acquireWriteIntentLock(ignored: String?): Boolean {
    if (lock.isWriteThread && (lock.isWriteIntentLocked || lock.isWriteAcquired)) {
      return false
    }
    lock.writeIntentLock()
    return true
  }


  fun releaseWriteIntentLock() {
    lock.writeIntentUnlock()
  }
}