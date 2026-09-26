package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import java.nio.file.Path

@Remote("com.jetbrains.performancePlugin.LogDirHandler", plugin = "com.jetbrains.performancePlugin")
private interface LogDirHandler {
  fun setLogDir(logDir: String)
}

/**
 * Switches the current driver-controlled IDE process to a fresh `idea.log` file.
 */
fun Driver.setLogDir(logDir: Path) {
  service<LogDirHandler>().setLogDir(logDir.toAbsolutePath().toString())
}
