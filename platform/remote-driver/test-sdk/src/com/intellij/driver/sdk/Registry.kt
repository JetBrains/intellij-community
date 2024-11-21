package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.setRegistry(key: String, value: String) {
  utility(Registry::class).get(key).setValue(value)
}

@Remote("com.intellij.openapi.util.registry.Registry")
interface Registry {
  fun `is`(key: String): Boolean

  fun get(key: String): RegistryValue
}

@Remote("com.intellij.openapi.util.registry.RegistryValue")
interface RegistryValue {
  fun setValue(value: String)
  fun setSelectedOption(option: String)
}