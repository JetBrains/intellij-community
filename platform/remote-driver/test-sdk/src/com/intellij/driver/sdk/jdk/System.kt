package com.intellij.driver.sdk.jdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

@Remote("java.lang.System")
interface System {
  fun getProperty(key: String): String
  fun setProperty(key: String, value: String)
}

fun Driver.getSystemProperty(key: String): String {
  return utility(System::class).getProperty(key)
}

fun Driver.setSystemProperty(key: String, value: String) {
  utility(System::class).setProperty(key, value)
}