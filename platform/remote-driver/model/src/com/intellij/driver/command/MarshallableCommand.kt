package com.intellij.driver.command

interface MarshallableCommand {
  fun storeToString(): String
}
