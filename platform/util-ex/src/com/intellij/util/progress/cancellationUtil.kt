// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CancellationUtil")

package com.intellij.util.progress

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.isInCancellableContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.io.await
import com.intellij.util.io.awaitFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock

private val LOG: Logger = Logger.getInstance("#com.intellij.util.progress")

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun sleepCancellable(millis: Long) {
  runBlockingCancellable {
    delay(millis)
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Semaphore.waitForCancellable() {
  if (isUp) {
    return
  }
  runBlockingCancellable {
    awaitFor()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Lock.lockCancellable() {
  LOG.assertTrue(isInCancellableContext())
  while (true) {
    ProgressManager.checkCanceled()
    if (tryLock(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
      return
    }
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> Lock.withLockCancellable(action: () -> T): T {
  lockCancellable()
  try {
    return action()
  }
  finally {
    unlock()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Lock.withLockCancellable(action: Runnable) {
  withLockCancellable(action::run)
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun pollCancellable(waiter: () -> Boolean) {
  runBlockingCancellable {
    while (true) {
      if (runInterruptible(block = waiter)) {
        return@runBlockingCancellable
      }
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> Future<T>.getCancellable(): T {
  return runBlockingCancellable {
    await()
  }
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> CompletableFuture<T>.getCancellable(): T {
  return runBlockingCancellable {
    asDeferred().await()
  }
}
