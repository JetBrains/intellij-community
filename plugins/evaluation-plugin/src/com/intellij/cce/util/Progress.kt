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
