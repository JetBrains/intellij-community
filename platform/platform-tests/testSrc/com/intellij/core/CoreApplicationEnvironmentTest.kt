// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.junit.Test

class CoreApplicationEnvironmentTest {
  @Test
  fun `test initialization`() {
    Disposer.newDisposable().use {
      CoreApplicationEnvironment(it)
    }
  }
}
