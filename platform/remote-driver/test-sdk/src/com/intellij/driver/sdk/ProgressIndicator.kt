package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.openapi.progress.ProgressIndicator")
interface ProgressIndicator {
  fun isRunning(): Boolean

  fun getText(): String?
}