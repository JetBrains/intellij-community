package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.openapi.project.DumbService")
interface DumbService {
  fun isDumb(): Boolean
}