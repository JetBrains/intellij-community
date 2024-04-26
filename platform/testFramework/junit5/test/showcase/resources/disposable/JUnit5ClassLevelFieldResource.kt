// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.disposable

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.DisposableResource
import com.intellij.testFramework.junit5.showcase.resources.disposable.JUnit5ClassLevelFieldResource.Companion.disposable1
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Class-level [Disposable] injected both into [disposable1] and arguments
 */
@TestApplication
@DisposableResource
class JUnit5ClassLevelFieldResource {
  companion object {
    @JvmStatic
    private lateinit var disposable1: Disposable
  }

  @Test
  fun test(disposable2: Disposable) {
    assertNotNull(disposable1)
    assertNotNull(disposable2)
    assertEquals(disposable1, disposable1)
    Disposer.register(disposable1, Disposer.newCheckedDisposable())
    Disposer.register(disposable2, Disposer.newCheckedDisposable())
  }
}