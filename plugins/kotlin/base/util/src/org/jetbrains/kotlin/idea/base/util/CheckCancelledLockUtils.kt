// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CheckCancelledLockUtils")
package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.IntellijInternalApi
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock

@IntellijInternalApi
inline fun <T> ReentrantReadWriteLock.writeWithCheckCanceled(action: () -> T): T {
    val rl = readLock()

    val readCount = if (writeHoldCount == 0) readHoldCount else 0
    repeat(readCount) { rl.unlock() }

    val wl = writeLock()
    while (!wl.tryLock(100, TimeUnit.MILLISECONDS)) {
        ProgressManager.checkCanceled()
    }
    try {
        return action()
    } finally {
        repeat(readCount) { rl.lock() }
        wl.unlock()
    }
}

/**
 * It is preferable to use [CheckCanceledLock] instead of [ReentrantLock] in case of mix of
 * [IntelliJ Read-Write locks](https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html) and regular locks.
 *
 * To acquire lock has to be cancellable action as read actions.
 */
@IntellijInternalApi
class CheckCanceledLock {
    val lock = ReentrantLock()

    inline fun <T> withLock(action: () -> T): T {
        while (!lock.tryLock(100, TimeUnit.MILLISECONDS)) {
            ProgressManager.checkCanceled()
        }
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}