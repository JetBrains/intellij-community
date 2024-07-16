// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.impl

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ResourceLifetimeTest {
  private lateinit var lifetime: ResourceLifetime

  @BeforeEach
  fun configureSut() {
    lifetime = ResourceLifetime()
  }

  @Test
  fun testClassLevel() {
    assertTrue(lifetime.classLevel)
    assertFalse(lifetime.methodLevel)
    assertTrue(lifetime.classLevel)
  }

  @Test
  fun testMethodLevel() {
    assertTrue(lifetime.methodLevel)
    assertTrue(lifetime.methodLevel)
    assertFalse(lifetime.classLevel)
    assertFalse(lifetime.classLevel)
    assertTrue(lifetime.methodLevel)
  }
}