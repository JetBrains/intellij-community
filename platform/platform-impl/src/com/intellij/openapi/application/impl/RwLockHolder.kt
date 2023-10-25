// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.ide.bootstrap.isImplicitReadOnEDTDisabled
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import java.lang.Deprecated
import java.lang.Runnable
import java.lang.Thread

@ApiStatus.Internal
class RwLockHolder(writeThread: Thread) : ThreadingSupport {
  @JvmField
  internal val lock: ReadMostlyRWLock = ReadMostlyRWLock(writeThread)

  private val myReadActionDispatcher = EventDispatcher.create(ReadActionListener::class.java)

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

  @Deprecated
  override fun addReadActionListener(listener: ReadActionListener) {
    myReadActionDispatcher.addListener(listener)
  }

  override fun addReadActionListener(listener: ReadActionListener, parent: Disposable) {
    myReadActionDispatcher.addListener(listener, parent)
  }

  @Deprecated
  override fun removeReadActionListener(listener: ReadActionListener) {
    myReadActionDispatcher.removeListener(listener)
  }

  override fun runReadAction(action: Runnable) {
    fireBeforeReadActionStart(action)
    val permit = lock.startRead()
    try {
      fireReadActionStarted(action)
      action.run()
      fireReadActionFinished(action)
    } finally {
      if (permit != null) {
        lock.endRead(permit)
        fireAfterReadActionFinished(action)
      }
    }
  }

  override fun <T> runReadAction(computation: Computable<T>): T {
    fireBeforeReadActionStart(computation)
    val permit = lock.startRead()
    try {
      fireReadActionStarted(computation)
      val rv = computation.compute()
      fireReadActionFinished(computation)
      return rv;
    } finally {
      if (permit != null) {
        lock.endRead(permit)
        fireAfterReadActionFinished(computation)
      }
    }
  }

  override fun <T, E : Throwable?> runReadAction(computation: ThrowableComputable<T, E>): T {
    fireBeforeReadActionStart(computation)
    val permit = lock.startRead()
    try {
      fireReadActionStarted(computation)
      val rv = computation.compute()
      fireReadActionFinished(computation)
      return rv;
    } finally {
      if (permit != null) {
        lock.endRead(permit)
        fireAfterReadActionFinished(computation)
      }
    }
  }

  private fun fireBeforeReadActionStart(action: Any) {
    try {
      myReadActionDispatcher.multicaster.beforeReadActionStart(action.javaClass)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionStarted(action: Any) {
    try {
      myReadActionDispatcher.multicaster.readActionStarted(action.javaClass)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireReadActionFinished(action: Any) {
    try {
      myReadActionDispatcher.multicaster.readActionFinished(action.javaClass)
    }
    catch (_: Throwable) {
    }
  }

  private fun fireAfterReadActionFinished(action: Any) {
    try {
      myReadActionDispatcher.multicaster.afterReadActionFinished(action.javaClass)
    }
    catch (_: Throwable) {
    }
  }
}