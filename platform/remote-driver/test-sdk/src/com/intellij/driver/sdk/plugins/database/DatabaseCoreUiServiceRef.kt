package com.intellij.driver.sdk.plugins.database

import com.intellij.driver.client.Remote

@Remote("com.intellij.database.view.DatabaseCoreUiService", plugin = "com.intellij.database")
interface DatabaseCoreUiServiceRef {
  fun extractExtensionScripts()
}