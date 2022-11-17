// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PropertyName", "LocalVariableName")

package org.jetbrains.concurrency

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Mitigates bugs related to access of non thread-safe fields. Inspired by https://jonnyzzz.com/blog/2017/03/01/guarded-by-lock/
 *
 * ```kotlin
 * class DomainClass {
 *   private val criticalSection = SynchronizedCriticalSection(object {
 *     var x = 123
 *     var y = 456
 *   })
 *
 *   fun incrementAndPrint() {
 *     // x += 1  // ERROR: No such variable.
 *     val value = criticalSection {
 *       x += 1
 *       y += 2
 *       "${Thread.currentThread()} x=$x y=$y\n"
 *     }
 *     print(value)
 *   }
 * }
 * ```
 */
class SynchronizedCriticalSection<Hidden : Any>(val _hidden: Hidden) {
  inline operator fun <T> invoke(block: Hidden.() -> T): T =
    synchronized(_hidden) {
      _hidden.block()
    }
}

/** See [SynchronizedCriticalSection] */
class LockCriticalSection<Hidden : Any>(val lock: java.util.concurrent.locks.Lock, val _hidden: Hidden) {
  constructor(_hidden: Hidden) : this(ReentrantLock(), _hidden)

  inline operator fun <T> invoke(block: Hidden.() -> T): T =
    lock.withLock {
      _hidden.block()
    }
}