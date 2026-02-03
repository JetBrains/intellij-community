package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote


val Driver.ideLogger: DriverTestLogger
  get() = utility(DriverTestLogger::class)

@Remote("com.jetbrains.performancePlugin.DriverTestLogger", plugin = "com.jetbrains.performancePlugin")
interface DriverTestLogger {
  fun info(text: String)
  fun warn(text: String)
  fun warn(t: Throwable)
}