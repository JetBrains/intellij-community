// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal abstract class InlineCompletionTestCase : BasePlatformTestCase() {
  // !!! VERY IMPORTANT !!!
  override fun runInDispatchThread(): Boolean {
    return false
  }
}
