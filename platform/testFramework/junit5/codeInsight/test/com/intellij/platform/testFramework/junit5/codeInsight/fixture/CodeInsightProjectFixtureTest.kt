// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

/**
 * Checks that [codeInsightProjectFixture] as an instance property gives each test a new project.
 */
@TestApplication
class CodeInsightProjectFixtureTest {
  companion object {
    private var previousProject: Project? = null
  }

  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  @Test
  fun `fixture temp dir is the project directory`() {
    assertEquals(myFixture.project.basePath, myFixture.tempDirPath)
  }

  @RepeatedTest(2)
  fun `project is new for each test`() {
    previousProject?.let { assertNotSame(it, myFixture.project) }
    previousProject = myFixture.project
  }
}
