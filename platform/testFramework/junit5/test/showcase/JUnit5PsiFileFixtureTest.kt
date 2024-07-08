// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class JUnit5PsiFileFixtureTest {
  private val project = projectFixture()
  private val psiFile = project.psiFile("Test.txt" ,"src", "Test file")

  @Test
  fun `fixture return same`() {
    assertEquals(psiFile.get(), psiFile.get())
  }
}