// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Test

/**
 * By default, codeInsightFixture() installs a VirtualFilePointerTracker in setUp and asserts in
 * tearDown that every pointer created since is gone, but the IdeaProjectTestFixture the junit5
 * codeInsightFixture uses does not close the project.
 *
 * So creating such a pointer during a test is reported as a leak even though nothing leaked.
 * This test exists to verify that the fixture is overriding shouldTrackVirtualFilePointers() to false.
 */
@TestApplication
class CodeInsightFixturePointerTrackerTest {
  private val tempPath = tempPathFixture()
  private val projectFixture = projectFixture(tempPath)
  private val project by projectFixture

  @Suppress("unused")
  private val module = projectFixture.moduleFixture()

  @Suppress("unused")
  private val codeInsight by codeInsightFixture(projectFixture, tempPath)

  @Test
  fun testProjectScopedPointerIsNotReportedAsLeaked() {
    val url = VfsUtilCore.pathToUrl(tempPath.get().resolve("some_header.h").toString())
    VirtualFilePointerManager.getInstance().create(url, project, null)
  }
}
