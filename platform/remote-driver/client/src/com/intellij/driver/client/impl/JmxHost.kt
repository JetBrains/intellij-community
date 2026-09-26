package com.intellij.driver.client.impl

data class JmxHost(
  val user: String? = null,
  val password: String? = null,
  val address: String
)