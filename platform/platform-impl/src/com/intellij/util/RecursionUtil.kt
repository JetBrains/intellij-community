// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Skips [action] execution if this or other block is executing with [this] "atomic".
 * Needs to break the recursion locally.
 */
inline fun AtomicBoolean.lockOrSkip(action: () -> Unit) {
  if (!compareAndSet(false, true)) return
  try {
    action()
  }
  finally {
    set(false)
  }
}