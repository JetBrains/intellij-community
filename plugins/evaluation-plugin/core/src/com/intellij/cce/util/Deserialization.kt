// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

inline fun <reified T> Map<String, *>.getAs(key: String): T {
  check(key in this.keys) { "$key not found. Existing keys: ${keys.toList()}" }
  val value = this.getValue(key)
  check(value is T) { "Unexpected type of key <$key> in config" }
  return value
}

inline fun <reified T> Map<String, *>.getIfExists(key: String): T? {
  if (key !in this.keys) return null
  val value = this.getValue(key)
  check(value is T) { "Unexpected type of key <$key> in config" }
  return value
}

inline fun <reified T> Map<String, *>.getIfExistsOrOverrideWithEnv(key: String): T? {
  System.getenv(key)?.let {
    val value = it
    check(value is T) { "Unexpected type of key <$key> in config" }
    return value
  }
  return getIfExists(key)
}

inline fun <reified T> Map<String, *>.getOrThrow(key: String): T {
  if (key !in this.keys) throw IllegalArgumentException("No key <$key> found in config")
  val value = this.getValue(key)
  check(value is T) { "Wrong type of key <$key> in config" }
  return value
}