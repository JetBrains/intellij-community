// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.ide.bootstrap.isImplicitReadOnEDTDisabled
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RwLockHolder(writeThread: Thread) {
  @JvmField
  internal val lock: ReadMostlyRWLock = ReadMostlyRWLock(writeThread)

  // @Throws(E::class)
  fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T {
    val writeIntentLock = acquireWriteIntentLock(computation.javaClass.getName())
    try {
      return computation.compute()
    }
    finally {
      if (writeIntentLock) {
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

  fun isWriteIntentLocked(): Boolean {
    return lock.isWriteThread && (lock.isWriteIntentLocked || lock.isWriteAcquired)
  }

  fun runWithoutImplicitRead(runnable: Runnable) {
    if (isImplicitReadOnEDTDisabled) {
      runnable.run()
      return
    }
    runWithDisabledImplicitRead(runnable)
  }

  private fun runWithDisabledImplicitRead(runnable: Runnable) {
    // This method is used to allow easily finding stack traces which violate disabled ImplicitRead
    val oldVal = lock.isImplicitReadAllowed
    try {
      lock.setAllowImplicitRead(false)
      runnable.run()
    }
    finally {
      lock.setAllowImplicitRead(oldVal)
    }
  }

  fun runWithImplicitRead(runnable: Runnable) {
    if (!isImplicitReadOnEDTDisabled) {
      runnable.run()
      return
    }
    runWithEnabledImplicitRead(runnable)
  }

  private fun runWithEnabledImplicitRead(runnable: Runnable) {
    // This method is used to allow easily find stack traces which violate disabled ImplicitRead
    val oldVal = lock.isImplicitReadAllowed
    try {
      lock.setAllowImplicitRead(true)
      runnable.run()
    }
    finally {
      lock.setAllowImplicitRead(oldVal)
    }
  }
}