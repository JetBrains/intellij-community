// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class JUnit5ModuleFixtureOnTempFixtureTest {
  private val tempDirFixture = tempPathFixture()
  private val moduleFixture = projectFixture().moduleFixture(
    pathFixture = tempDirFixture,
    addPathToSourceRoot = true
  )

  @Test
  fun moduleOnPathTest() {
    val vfsPath = VirtualFileManager.getInstance().findFileByNioPath(tempDirFixture.get())!!
    assertEquals(moduleFixture.get(), ModuleUtilCore.findModuleForFile(vfsPath, moduleFixture.get().project))
  }
}
