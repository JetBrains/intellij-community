package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote


fun Driver.getAllRegistryValues(): List<RegistryValue> {
  return utility(Registry::class).getAll()
}

fun Driver.getRegistry(key: String): RegistryValue {
  return utility(Registry::class).get(key)
}

fun Driver.setRegistry(key: String, value: String) {
  getRegistry(key).setValue(value)
}

fun Driver.setRegistry(key: String, value: Int) {
  utility(Registry::class).get(key).setValue(value)
}

fun Driver.setRegistry(key: String, value: Boolean) {
  utility(Registry::class).get(key).setValue(value)
}

@Remote("com.intellij.openapi.util.registry.Registry")
interface Registry {
  fun `is`(key: String): Boolean

  fun get(key: String): RegistryValue
  fun getAll(): List<RegistryValue>
}

@Remote("com.intellij.openapi.util.registry.RegistryValue")
interface RegistryValue {
  fun setSelectedOption(option: String)

  fun setValue(value: String)
  fun setValue(value: Int)
  fun setValue(value: Boolean)

  fun asBoolean(): Boolean
  fun asInteger(): Int
  fun asString(): String
}