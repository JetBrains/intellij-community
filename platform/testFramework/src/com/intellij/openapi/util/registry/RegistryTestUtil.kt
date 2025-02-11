// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("RegistryTestUtil")
package com.intellij.openapi.util.registry

inline fun RegistryValue.withValue(tempValue: Boolean, crossinline block: () -> Unit) {
  val currentValue = asBoolean()
  try {
    setValue(tempValue)
    block()
  }
  finally {
    setValue(currentValue)
  }
}

inline fun RegistryValue.withValue(tempValue: String, crossinline block: () -> Unit) {
  val currentValue = asString()
  try {
    setValue(tempValue)
    block()
  }
  finally {
    setValue(currentValue)
  }
}
