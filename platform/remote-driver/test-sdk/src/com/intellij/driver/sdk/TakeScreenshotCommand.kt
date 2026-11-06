package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.commands.TakeScreenshotCommand", plugin = "com.jetbrains.performancePlugin")
private interface TakeScreenshotCommand {
  fun takeScreenshot(childFolder: String?)
}

/**
 * Paints each IDE window to `<IDE log dir>/screenshots/<NNN>_<childFolder>/<window name>.png`, as the heartbeat screenshots do.
 *
 * [Driver.takeScreenshot] writes one image of the screen.
 * This function writes one image of each window, at the full resolution of a HiDPI screen. It writes nothing while no project is open.
 */
fun Driver.takeScreenshotOfAllWindows(childFolder: String) {
  new(TakeScreenshotCommand::class).takeScreenshot(childFolder)
}
