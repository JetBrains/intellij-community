// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.fixtures.project

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Inject project in an instance field: one project for the whole class
 */
@TestApplication
class JUnit5ClassLeveLProject {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun ensureProject() {
    val project = projectFixture.get()
    Assertions.assertFalse(project.isDisposed)
    Disposer.register(project) {

    }
  }
}