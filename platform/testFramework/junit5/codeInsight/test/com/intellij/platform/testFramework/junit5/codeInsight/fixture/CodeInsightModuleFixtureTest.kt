// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

/**
 * Checks that [codeInsightModuleFixture] shares one project per class and creates a new directory and module for each test.
 * It also checks that the files of an earlier test are no longer valid in the VFS.
 */
@TestApplication
class CodeInsightModuleFixtureTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private var previousProject: Project? = null
    private var previousFixture: CodeInsightTestFixture? = null
    private var previousModule: Module? = null
    private var previousTempDirPath: String? = null
    private var previousFile: VirtualFile? = null
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `fixture temp dir is not the project directory`() {
    assertNotEquals(myFixture.project.basePath, myFixture.tempDirPath)
  }

  @RepeatedTest(2)
  fun `project is shared and directory and module are new for each test`() {
    assertNull(myFixture.tempDirFixture.getFile("earlier.txt"))
    val file = myFixture.addFileToProject("earlier.txt", "").virtualFile

    if (previousProject != null) {
      assertSame(previousProject, myFixture.project)
      assertNotSame(previousFixture, myFixture)
      assertNotSame(previousModule, myFixture.module)
      assertTrue(previousModule!!.isDisposed)
      assertNotEquals(previousTempDirPath, myFixture.tempDirPath)
      assertFalse(previousFile!!.isValid)
    }
    previousProject = myFixture.project
    previousFixture = myFixture
    previousModule = myFixture.module
    previousTempDirPath = myFixture.tempDirPath
    previousFile = file
  }
}
