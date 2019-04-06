// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.testGuiFramework.util.guilog
import com.intellij.testGuiFramework.util.logInfo

object GuiTestNameHolder {
  @Volatile
  private var _testName: String = "FirstStart"
  val testName: String
    get() {
      return synchronized(this) { _testName }
    }

  fun initialize(name: String) {
    synchronized(this) {
      _testName = name
    }
  }
}