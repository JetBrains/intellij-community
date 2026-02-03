// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Support fields declared at parent level.
 * Implementation inheritance is a bad pattern, avoid it and use extensions instead
 */
@TestApplication
open class AbstractTestCase {
  private data class DisposableState(var isDisposed: Boolean = false)

  companion object {
    val classLevelDisposable = disposableFixture()

    private val reusableState by testFixture {
      val disposableState = DisposableState()
      initialized(disposableState) {
        disposableState.isDisposed = true
      }
    }
  }

  protected val testLevelProject = projectFixture()

  // This test is run both for the AbstractTestCase and the JUnit5InheritanceFixture and checks that reusableState is reset for each
  // inheritor of the class.
  @Test
  fun ensureParentFixturesCanBeReused() {
    assertFalse(reusableState.isDisposed)
  }
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