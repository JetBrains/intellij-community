package com.intellij.driver.sdk.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.getKeymapManager() = service(KeymapManager::class)

@Remote("com.intellij.openapi.keymap.KeymapManager")
interface KeymapManager {
  fun getActiveKeymap(): Keymap
}

@Remote("com.intellij.openapi.keymap.Keymap")
interface Keymap {
  fun getName(): String
}