// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

@TestOnly
@Internal
fun assertDisposerEmpty() {
  try {
    Disposer.assertIsEmpty(true)
  }
  catch (e: AssertionError) {
    publishHeapDump("disposerNonEmpty")
    throw e
  }
  catch (e: Exception) {
    publishHeapDump("disposerNonEmpty")
    throw e
  }
}
