package com.intellij.driver.sdk.plugins.database

import com.intellij.driver.client.Remote

@Remote("com.intellij.database.view.DatabaseCoreUiService", plugin = "com.intellij.database")
interface DatabaseCoreUiServiceRef {
  fun extractExtensionScripts()
}

@Remote("com.intellij.database.view.DbTestingService", plugin = "com.intellij.database")
interface DbTestingServiceRef {
  fun openAndWaitDataLoaded(path: String)
}