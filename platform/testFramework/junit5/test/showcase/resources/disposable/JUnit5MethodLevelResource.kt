// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.disposable

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.ResourceExtensionApi
import com.intellij.testFramework.junit5.resources.asExtension
import com.intellij.testFramework.junit5.resources.create
import com.intellij.testFramework.junit5.resources.providers.DisposableProvider
import com.intellij.testFramework.junit5.showcase.resources.ResourceCounter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * [Disposable] created on method level and disposed after each test.
 * [checkLeaks] proofs it
 */
@TestApplication
class JUnit5MethodLevelResource {
  @JvmField
  @RegisterExtension
  @Order(0)
  val checkLeaks = AfterEachCallback {
    resourceCounter.ensureEmpty()
  }

  @JvmField
  @RegisterExtension
  @Order(1)
  val ext = DisposableProvider().asExtension()


  private val resourceCounter = ResourceCounter()

  private lateinit var disposableField: Disposable


  @Test
  fun test(disposable1: Disposable, disposable2: Disposable) {
    assertEquals(disposable1, disposable2, "Instance level must use same resource")
    assertEquals(disposable1, disposableField, "Instance level must use same resource")
    resourceCounter.acquire(disposable1)
    runBlocking {
      resourceCounter.acquire(ext.create())
    }
    resourceCounter.ensureNotEmpty()
  }

  @Test
  fun test2(disposable: Disposable) {
    resourceCounter.ensureEmpty() // Method-level resource should already be destroyed
    resourceCounter.acquire(disposable)
    resourceCounter.acquire(disposableField)
  }
}