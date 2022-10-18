// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.openapi.application.ex.PathManagerEx
import java.io.File
import java.nio.file.Path

class EntityCodeGenTest : CodeGenerationTestBase() {
  override val testDataDirectory: File
    get() = File(PathManagerEx.getCommunityHomePath() + "/plugins/devkit/intellij.devkit.workspaceModel/tests/testData/$testDirectoryName")

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("before", "")
  }

  fun testSimpleCase() {
    doTest()
  }

  fun testFinalProperty() {
    doTest()
  }

  fun testDefaultProperty() {
    doTest()
  }

  fun testSymbolicId() {
    doTest()
  }

  fun testUpdateOldCode() {
    doTest()
  }

  fun testEntityWithCollections() {
    doTest()
  }

  fun testRefsFromAnotherModule() {
    doTest()
  }

  fun testRefsSetNotSupported() {
    assertThrows(IllegalStateException::class.java) { doTest() }
  }

  fun testHierarchyOfEntities() {
    doTest()
  }

  fun testVirtualFileUrls() {
    doTest()
  }

  fun testUnknownPropertyType() {
    doTest(keepUnknownFields = true)
  }

  fun testAddCopyrightComment() {
    doTest(keepUnknownFields = true)
  }

  fun testBothLinksAreParents() {
    assertThrows(IllegalStateException::class.java) { doTest() }
  }

  fun testBothLinksAreChildren() {
    assertThrows(IllegalStateException::class.java) { doTest() }
  }

  private fun doTest(keepUnknownFields: Boolean = false) {
    generateAndCompare(getExpectedDir(), getExpectedDir().resolve("gen"), keepUnknownFields)
  }


  private fun getExpectedDir(): Path {
    return testDataDirectory.toPath().resolve("after")
  }
}