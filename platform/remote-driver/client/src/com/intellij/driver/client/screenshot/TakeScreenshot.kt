package com.intellij.driver.client.screenshot

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.commands.TakeScreenshotCommand", plugin = "com.jetbrains.performancePlugin")
interface TakeScreenshot {
  fun takeScreenshot(childFolder: String?)
}