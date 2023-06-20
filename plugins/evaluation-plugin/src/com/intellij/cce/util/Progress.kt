// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

interface Progress {
  fun start() = Unit
  fun setProgress(fileName: String, text: String, fraction: Double)
  fun finish() = Unit
  fun isCanceled(): Boolean

  fun wrapWithProgress(block: (Progress) -> Unit) {
    start()
    block(this)
    finish()
  }
}
