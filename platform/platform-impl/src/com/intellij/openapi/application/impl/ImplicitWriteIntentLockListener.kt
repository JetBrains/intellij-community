// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.util.concurrency.ThreadingAssertions

internal class ImplicitWriteIntentLockListener : ReadActionListener, WriteIntentReadActionListener, WriteActionListener {

  private val implicitLockStack: ThreadLocal<ArrayList<Boolean>> = ThreadLocal.withInitial { ArrayList(0) }

  override fun writeIntentReadActionStarted(action: Class<*>) {
    lockedActionStarted()
  }

  override fun writeIntentReadActionFinished(action: Class<*>) {
    lockedActionEnded()
  }

  override fun readActionStarted(action: Class<*>) {
    lockedActionStarted()
  }

  override fun readActionFinished(action: Class<*>) {
    lockedActionEnded()
  }

  override fun writeActionStarted(action: Class<*>) {
    lockedActionStarted()
  }

  override fun writeActionFinished(action: Class<*>) {
    lockedActionEnded()
  }

  private fun lockedActionStarted() {
    val prevImplicitLock = ThreadingAssertions.isImplicitLockOnEDT()
    implicitLockStack.get().add(prevImplicitLock)
    ThreadingAssertions.setImplicitLockOnEDT(false)
  }

  private fun lockedActionEnded() {
    val topValue = implicitLockStack.get().removeLast()
    ThreadingAssertions.setImplicitLockOnEDT(topValue)
  }
}