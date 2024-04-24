// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.disposable

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.DisposableResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Class level disposable in automatic manner
 */
@TestApplication
@DisposableResource
class JUnit5ClassLevelResourceAnnotation {

  @Test
  fun test(disposable1: Disposable, disposable2: Disposable) {
    assertEquals(disposable1, disposable2, "Class level must use same resource")
    Disposer.register(disposable1, Disposable {
      thisLogger().info("It must be disposed. Otherwise, leak would be reported")
    })
  }

}