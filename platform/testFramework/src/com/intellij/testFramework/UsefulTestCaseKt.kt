// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UsefulTestCaseKt")
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer


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

