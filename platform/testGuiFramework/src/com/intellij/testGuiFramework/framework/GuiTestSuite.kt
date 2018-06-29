// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith

@RunWith(GuiTestSuiteRunner::class)
open class GuiTestSuite {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUp() {
    }

    @AfterClass
    @JvmStatic
    fun tearDown() {
    }
  }

}
