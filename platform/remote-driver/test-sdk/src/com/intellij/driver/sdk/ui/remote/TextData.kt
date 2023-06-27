package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.remotedriver.dataextractor.TextData",
        plugin = REMOTE_ROBOT_MODULE_ID)
interface TextData {
  val text: String
  val point: Point
  val bundleKey: String?
}