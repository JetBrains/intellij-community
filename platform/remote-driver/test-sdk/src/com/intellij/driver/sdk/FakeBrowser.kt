package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.replaceIdeBrowser(): FakeBrowser {
  utility(ReplaceBrowser::class).replaceBrowser()
  return new(FakeBrowser::class)
}

@Remote("com.jetbrains.performancePlugin.FakeBrowser", plugin = "com.jetbrains.performancePlugin")
interface FakeBrowser {
  fun getLatestUrl(): String?
  fun open(url: String)
}

@Remote("com.jetbrains.performancePlugin.commands.ReplaceBrowser", plugin = "com.jetbrains.performancePlugin")
interface ReplaceBrowser {
  fun replaceBrowser()
}
