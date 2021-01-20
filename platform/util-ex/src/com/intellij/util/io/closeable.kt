// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CloseableUtil")

package com.intellij.util.io

inline fun <T : AutoCloseable, R> T.runClosingOnFailure(block: T.() -> R): R {
  return try {
    block()
  }
  catch (e: Throwable) {
    when (this) {
      is MultiCloseable -> close(e)  // special case to avoid unnecessary suppressed exceptions nesting level
      else -> use { throw e }
    }
    error("unreachable")
  }
}
