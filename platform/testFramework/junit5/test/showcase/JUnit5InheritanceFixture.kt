// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Support fields declared at parent level.
 * Implementation inheritance is a bad pattern, avoid it and use extensions instead
 */
@TestApplication
open class AbstractTestCase {
  companion object {
    val classLevelDisposable = disposableFixture()
  }

  protected val testLevelProject = projectFixture()
}

class JUnit5InheritanceFixture : AbstractTestCase() {
  private val moduleFixture = testLevelProject.moduleFixture()

  @Test
  fun ensureParentFixturesInitialized() {
    assertNotNull(classLevelDisposable.get())
    assertNotNull(testLevelProject.get())
  }

  @Test
  fun ensureChildrenInheritParent() {
    assertNotNull(moduleFixture.get())
  }
}