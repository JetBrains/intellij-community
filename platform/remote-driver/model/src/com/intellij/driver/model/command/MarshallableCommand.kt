package com.intellij.driver.model.command

interface MarshallableCommand {
  fun storeToString(): String
}
