// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.CodeGeneratorVersions
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class GeneratedCodeVersionsTest {
  private var prevApi = 0
  private var prevImpl = 0

  @BeforeEach
  fun setUp() {
    prevApi = CodeGeneratorVersions.API_VERSION
    prevImpl = CodeGeneratorVersions.IMPL_VERSION
  }

  @AfterEach
  fun tearDown() {
    CodeGeneratorVersions.API_VERSION = prevApi
    CodeGeneratorVersions.IMPL_VERSION = prevImpl
    CodeGeneratorVersions.checkApiInImpl = true
    CodeGeneratorVersions.checkApiInInterface = true
  }

  @Test
  fun `test api builder interface`() {
    val emptyBuilder = createEmptyBuilder()
    assertTrue(CodeGeneratorVersions.checkApiInInterface)
    CodeGeneratorVersions.API_VERSION = 10_000
    try {
      emptyBuilder.addEntity(ParentEntity("", MySource))
    }
    catch (e: AssertionError) {
      assertTrue("API" in e.message!!)
      assertTrue("'10000'" in e.message!!)
      assertTrue(prevApi.toString() in e.message!!)
      return
    }
    fail("No exception thrown")
  }

  @Test
  fun `test api builder impl code`() {
    CodeGeneratorVersions.checkApiInInterface = false
    assertTrue(CodeGeneratorVersions.checkApiInImpl)
    CodeGeneratorVersions.API_VERSION = 10_000
    val emptyBuilder = createEmptyBuilder()
    try {
      emptyBuilder.addEntity(ParentEntity("", MySource))
    }
    catch (e: AssertionError) {
      assertTrue("API" in e.message!!)
      assertTrue("'10000'" in e.message!!)
      assertTrue(prevApi.toString() in e.message!!)
      assertTrue("implementation" in e.message!!)
      return
    }
    fail("No exception thrown")
  }

  @Test
  fun `test impl builder impl code`() {
    CodeGeneratorVersions.checkApiInInterface = false
    CodeGeneratorVersions.checkApiInImpl = false
    assertTrue(CodeGeneratorVersions.checkImplInImpl)
    CodeGeneratorVersions.IMPL_VERSION = 10_000
    val emptyBuilder = createEmptyBuilder()
    try {
      emptyBuilder.addEntity(ParentEntity("", MySource))
    }
    catch (e: AssertionError) {
      assertTrue("IMPL" in e.message!!)
      assertTrue("'10000'" in e.message!!)
      assertTrue(prevImpl.toString() in e.message!!)
      return
    }
    fail("No exception thrown")
  }
}
