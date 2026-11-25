package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import java.nio.file.Path

/**
 * Opens [path] with the `%openFile` command of the performance plugin and waits at most 30 seconds.
 */
@Deprecated("Use openFile. It opens the file through the project and waits for the code analysis.")
fun Driver.openFileViaCommand(path: String): Unit = step("Open file ${Path.of(path).fileName}") {
  waitForProjectOpen()
  service<PlaybackRunnerServiceRef>().runScript(singleProject(), "%openFile -file ${path.replace(" ", "SPACE_SYMBOL")} -timeout 30 -unwfca")
}

@Remote("com.jetbrains.performancePlugin.PlaybackRunnerService", plugin = "com.jetbrains.performancePlugin")
internal interface PlaybackRunnerServiceRef {
  fun runScript(project: Project, script: String)
}
