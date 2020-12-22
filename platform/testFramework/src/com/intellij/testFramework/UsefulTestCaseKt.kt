// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UsefulTestCaseKt")
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry


private fun setSystemProperty(key: String, value: String?) : String? {
  return if (value != null) {
    System.setProperty(key, value)
  } else {
    System.clearProperty(key)
  }
}

fun UsefulTestCase.setSystemPropertyForTest(key: String, value: Boolean) = setSystemPropertyForTest(key, "$value")

fun UsefulTestCase.setSystemPropertyForTest(key: String, value: String?) {
  val prev = setSystemProperty(key, value)
  Disposer.register(testRootDisposable, Disposable {
    setSystemProperty(key, prev)
  })
}

private fun UsefulTestCase.bindRegistryKeyReset(key: String) {
  Disposer.register(testRootDisposable, Disposable {
    Registry.get(key).resetToDefault()
  })
}

fun UsefulTestCase.setRegistryPropertyForTest(key: String, value: String) {
  Registry.get(key).setValue(value)
  bindRegistryKeyReset(key)
}

fun UsefulTestCase.setRegistryPropertyForTest(key: String, value: Boolean) {
  Registry.get(key).setValue(value)
  bindRegistryKeyReset(key)
}
