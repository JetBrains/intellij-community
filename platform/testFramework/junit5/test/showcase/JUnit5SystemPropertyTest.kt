// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.SystemProperty
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

@SystemProperty("hello-class", "world-class")
@SystemProperty("hello-class2", "world-class2")
class JUnit5SystemPropertyTest {

  companion object {

    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      assertClassProperties()
      assertNoMethodProperties()
    }

    @JvmStatic
    @AfterAll
    fun afterAll() {
      assertClassProperties()
      assertNoMethodProperties()
    }

    private fun assertClassProperties() {
      assertEquals("world-class", System.getProperty("hello-class"))
      assertEquals("world-class2", System.getProperty("hello-class2"))
    }

    private fun assertNoMethodProperties() {
      assertEquals(null, System.getProperty("hello-method"))
      assertEquals(null, System.getProperty("hello-method2"))
    }
  }

  init {
    assertClassProperties()
    assertNoMethodProperties()
  }

  @BeforeEach
  fun beforeEach() {
    assertClassProperties()
    assertNoMethodProperties()
  }

  @AfterEach
  fun afterEach() {
    assertClassProperties()
    assertNoMethodProperties()
  }

  @Test
  fun `class system property`() {
    assertClassProperties()
    assertNoMethodProperties()
  }

  @SystemProperty("hello-class", "world-class-overridden")
  @Test
  fun `override system property`() {
    assertEquals("world-class-overridden", System.getProperty("hello-class"))
  }

  @SystemProperty("hello-class", "")
  @Test
  fun `clear system property`() {
    assertEquals(null, System.getProperty("hello-class"))
  }

  @SystemProperty("hello-method", "world-method")
  @SystemProperty("hello-method2", "world-method2")
  @Test
  fun `multiple properties`() {
    assertClassProperties()
    assertEquals("world-method", System.getProperty("hello-method"))
    assertEquals("world-method2", System.getProperty("hello-method2"))
  }

  @SystemProperty("hello-method", "world-method")
  @SystemProperty("hello-method", "world-method2")
  @Test
  fun `multiple properties with same key`() {
    assertClassProperties()
    assertEquals("world-method2", System.getProperty("hello-method"))
  }
}
