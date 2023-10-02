package com.intellij.driver.sdk.jdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

@Remote("java.lang.System")
interface System {
  fun getProperty(key: String): String
}

fun Driver.getSystemProperty(key: String): String {
  return utility(System::class).getProperty(key)
}