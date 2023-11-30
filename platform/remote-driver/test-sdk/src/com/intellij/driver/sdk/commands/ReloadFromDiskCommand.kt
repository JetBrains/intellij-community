package com.intellij.driver.sdk.commands

import com.intellij.driver.client.Remote

@Remote(value = "com.jetbrains.performancePlugin.commands.ReloadFilesCommand",
        plugin = "com.jetbrains.performancePlugin")
interface ReloadFromDiskCommand {
  fun synchronizeFiles(filePaths: List<String>)
}