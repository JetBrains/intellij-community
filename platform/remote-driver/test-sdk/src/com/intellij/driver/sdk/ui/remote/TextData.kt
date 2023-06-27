package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.remotedriver.dataextractor.TextData", plugin = "com.jetbrains.performancePlugin")
interface TextData {
  val text: String
  val point: Point
  val bundleKey: String?
}