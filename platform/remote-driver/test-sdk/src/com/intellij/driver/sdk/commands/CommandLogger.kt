package com.intellij.driver.sdk.commands

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.CommandLogger", plugin = "com.jetbrains.performancePlugin")
interface CommandLogger