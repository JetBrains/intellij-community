package com.intellij.driver.sdk.commands

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.PlaybackRunnerExtended", plugin = "com.jetbrains.performancePlugin")
interface PlaybackRunnerExtended {
  fun runBlocking(timeoutsMs: Long = 0)
}