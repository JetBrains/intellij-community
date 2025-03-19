// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@TestApplication
class JUnit5PsiFileFixtureTest {
  private val project = projectFixture()
  private val module = project.moduleFixture("src")
  private val sourceRoot = module.sourceRootFixture()
  private val psiFile = sourceRoot.psiFileFixture("Test.txt", "Test file")
  private val psiFile2 = sourceRoot.psiFileFixture("Test2.txt", "Test file 2")

  @Test
  fun `fixture return same`() {
    assertEquals(psiFile.get(), psiFile.get())
    assertEquals(psiFile2.get(), psiFile2.get())
    assertNotEquals(psiFile.get(), psiFile2.get())
  }

  @Test
  fun `fixture content equal`() {
    runBlocking {
      readAction {
        assertEquals(psiFile.get().text, "Test file")
        assertEquals(psiFile2.get().text, "Test file 2")
        assertEquals(psiFile.get().text, psiFile.get().text)
        assertEquals(psiFile2.get().text, psiFile2.get().text)
      }
    }
  }
}