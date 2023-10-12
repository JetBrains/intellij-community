// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.ide.bootstrap.isImplicitReadOnEDTDisabled
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RwLockHolder(writeThread: Thread) : ThreadingSupport {
  @JvmField
  internal val lock: ReadMostlyRWLock = ReadMostlyRWLock(writeThread)

  // @Throws(E::class)
  override fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T {
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

  override fun acquireWriteIntentLock(ignored: String?): Boolean {
    if (lock.isWriteThread && (lock.isWriteIntentLocked || lock.isWriteAcquired)) {
      return false
    }
    lock.writeIntentLock()
    return true
  }

  override fun releaseWriteIntentLock() {
    lock.writeIntentUnlock()
  }

  override fun isWriteIntentLocked(): Boolean {
    return lock.isWriteThread && (lock.isWriteIntentLocked || lock.isWriteAcquired)
  }

  override fun runWithoutImplicitRead(runnable: Runnable) {
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

  override fun runWithImplicitRead(runnable: Runnable) {
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