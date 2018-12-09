// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij

import junit.framework.JUnit4TestAdapter
import junit.framework.JUnit4TestAdapterCache

/**
 * Use this runner instead [AllTests] to run tests from IDE.
 */
class AllTestsWithIDERunner {
  companion object {

    @JvmStatic
    fun suite() = object : com.intellij.TestAll("") {
      override fun createJUnit4Adapter(testCaseClass: Class<*>) =
        JUnit4TestAdapter(testCaseClass, JUnit4TestAdapterCache.getDefault())
    }
  }
}