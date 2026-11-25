package com.intellij.driver.sdk.jdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.util.system.OS

@Remote("java.lang.System")
interface System {
  fun getProperty(key: String): String
  fun setProperty(key: String, value: String)
}

fun Driver.getSystemProperty(key: String): String {
  return utility(System::class).getProperty(key)
}

val Driver.isRemoteMac: Boolean
  get() = remoteOs == OS.macOS

val Driver.osName: String
  get() = getSystemProperty("os.name")

val Driver.remoteOs: OS
  get() = OS.fromString(osName)

fun Driver.setSystemProperty(key: String, value: String) {
  utility(System::class).setProperty(key, value)
}