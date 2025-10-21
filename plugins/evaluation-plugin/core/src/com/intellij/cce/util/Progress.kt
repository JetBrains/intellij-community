// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

interface Progress {
  fun start() = Unit
  fun setProgress(fileName: String, text: String, fraction: Double)
  fun finish() = Unit
  fun isCanceled(): Boolean

  suspend fun <T> wrapWithProgress(block: suspend (Progress) -> T): T {
    start()
    val result = block(this)
    finish()
    return result
  }
}
