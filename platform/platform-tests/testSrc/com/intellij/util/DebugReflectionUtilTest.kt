// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.DebugReflectionUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
class DebugReflectionUtilTest {

  @Test
  fun `isInitialized returns false for not initialized class`() {
    assertFalse {
      DebugReflectionUtil.isInitialized(Thread.currentThread().contextClassLoader, "com.intellij.util.ThisClassWillFailOnInitialization")
    }
    assertThrows<ExceptionInInitializerError> {
      ThisClassWillFailOnInitialization()
    }
  }


  @Test
  fun `isInitialized returns false for not initialized class 2`() {
    assertFalse {
      DebugReflectionUtil.isInitialized(Thread.currentThread().contextClassLoader, "com.intellij.util.ThisClassWillNotFailOnInitialization")
    }
    ThisClassWillNotFailOnInitialization()
    assertTrue {
      DebugReflectionUtil.isInitialized(Thread.currentThread().contextClassLoader, "com.intellij.util.ThisClassWillNotFailOnInitialization")
    }
  }
}
