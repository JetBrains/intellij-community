package com.intellij.driver.model

import java.io.Serializable

class DriverIllegalStateException : IllegalStateException, Serializable {
  constructor(message: String) : super(message)
  constructor(cause: Throwable) : super(cause)
  constructor(message: String, cause: Throwable) : super(message, cause)
}