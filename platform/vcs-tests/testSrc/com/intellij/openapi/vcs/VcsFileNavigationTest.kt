// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class VcsFileNavigationTest {
  private val project = projectFixture(openAfterCreation = true)
  private val manager = project.fileEditorManagerFixture()

  @Test
  fun `opening repository revisions selects the last file`(): Unit = timeoutRunBlocking {
    val files = listOf(LightVirtualFile("first.txt"), LightVirtualFile("second.txt"))
    val opened = withContext(Dispatchers.EDT) { VcsFileNavigation.requestNavigateToVcsFiles(project.get(), files) }
    assertTrue(opened.await())
    withContext(Dispatchers.EDT) {
      assertTrue(files.all(manager.get()::isFileOpen))
      assertEquals(files.last(), manager.get().currentFile)
    }
  }
}
