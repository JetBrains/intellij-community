package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.openapi.progress.TaskInfo")
interface TaskInfo {
  fun getTitle(): String?

  fun isCancellable(): Boolean
}