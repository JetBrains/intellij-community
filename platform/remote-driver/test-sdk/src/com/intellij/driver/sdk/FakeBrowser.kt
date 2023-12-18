package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.FakeBrowser", plugin = "com.jetbrains.performancePlugin")
interface FakeBrowser {
  fun getLatestUrl(): String?
}