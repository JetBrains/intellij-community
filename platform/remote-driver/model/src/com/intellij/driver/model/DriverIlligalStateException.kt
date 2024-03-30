package com.intellij.driver.model

class DriverIlligalStateException : IllegalStateException {
  constructor(message: String) : super(message)
  constructor(cause: Throwable) : super(cause)
  constructor(message: String, cause: Throwable) : super(message, cause)
}