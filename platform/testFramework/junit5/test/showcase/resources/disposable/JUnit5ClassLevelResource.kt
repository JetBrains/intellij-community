// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.disposable

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.resources.ResourceExtensionApi
import com.intellij.testFramework.junit5.resources.asExtension
import com.intellij.testFramework.junit5.resources.create
import com.intellij.testFramework.junit5.resources.providers.DisposableProvider
import com.intellij.testFramework.junit5.showcase.resources.ResourceCounter
import com.intellij.testFramework.junit5.showcase.resources.disposable.JUnit5ClassLevelResource.Companion.checkLeaks
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Class level [Disposable] created programmatically.
 * [checkLeaks] will be created before and destroyed ([AfterAllCallback]) after
 */
class JUnit5ClassLevelResource {

  companion object {
    @JvmStatic
    @RegisterExtension
    @Order(0)
    val checkLeaks = AfterAllCallback {
      resourceCounter.ensureEmpty()
    }

    @JvmStatic
    @RegisterExtension
    @Order(1)
    val ext = DisposableProvider().asExtension()

    private val resourceCounter = ResourceCounter()
  }


  @Test
  fun test(disposable1: Disposable, disposable2: Disposable) {
    assertEquals(disposable1, disposable2, "Class level must use same resource")
    resourceCounter.acquire(disposable1)
    resourceCounter.ensureNotEmpty()
  }

  @Test
  fun testManualDestroy(): Unit = runBlocking {
    val disposable = ext.create(disposeOnExit = false)
    Disposer.dispose(disposable)
  }

  @Test
  fun testManualResource(): Unit = runBlocking {
    resourceCounter.acquire(ext.create())

    // Manually created resources are different
    assertNotEquals(ext.create(), ext.create())
  }
}