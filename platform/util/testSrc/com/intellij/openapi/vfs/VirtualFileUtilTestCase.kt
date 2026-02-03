// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

@TestApplication
abstract class VirtualFileUtilTestCase {

  private lateinit var fileFixture: TempDirTestFixture
  private lateinit var testRoot: VirtualFile

  val root: VirtualFile
    get() = testRoot

  val nioRoot: Path
    get() = testRoot.toNioPath()

  @BeforeEach
  fun setUp() {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    fileFixture.setUp()
    testRoot = Path.of(fileFixture.tempDirPath).refreshAndGetVirtualDirectory()
  }

  @AfterEach
  fun tearDown() {
    fileFixture.tearDown()
  }
}