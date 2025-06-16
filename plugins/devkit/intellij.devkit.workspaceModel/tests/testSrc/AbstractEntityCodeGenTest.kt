// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel

import com.intellij.openapi.application.ex.PathManagerEx
import org.junit.jupiter.api.Assertions
import java.io.File
import java.nio.file.Path

abstract class AbstractEntityCodeGenTest : CodeGenerationTestBase() {
  override val testDataDirectory: File
    get() = File(PathManagerEx.getCommunityHomePath() + "/plugins/devkit/intellij.devkit.workspaceModel/tests/testData/codeGen/$testDirectoryName")

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

  fun testEntityWithChildrenCollection() {
    doTest()
  }

  fun testEntityWithDifferentChildrenTargets() {
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
    doTest(processAbstractTypes = true)
  }

  fun testAddCopyrightComment() {
    doTest(processAbstractTypes = true)
  }

  fun testImports() {
    doTest()
  }

  fun testOpenClassProperty() {
    doTest(processAbstractTypes = true)
  }

  fun testPackages() {
    doTest()
  }

  fun testPropertiesOrder() {
    doTest()
  }

  fun testBothLinksAreParents() {
    doTestAndCheckErrorMessage("Both fields MainEntity#secondaryEntity and SecondaryEntity#mainEntity are marked as parent. Probably both properties are annotated with @Parent, while only one should be.")
  }

  fun testBothLinksAreChildren() {
    doTestAndCheckErrorMessage("Both fields MainEntity#secondaryEntities and SecondaryEntity#mainEntity are marked as child. Probably @Parent annotation is missing from one of the properties.")
  }

  fun testChildrenShouldBeNullable() {
    doTestAndCheckErrorMessage("Failed to generate code for secondaryEntity (MainEntity): Child references should always be nullable")
  }

  fun testVarFieldForbidden() {
    doTestAndCheckErrorMessage("Failed to generate code for isValid (MainEntity): An immutable interface can't contain mutable properties")
  }

  fun testSymbolicIdNotDeclared() {
    doTestAndCheckErrorMessage("Failed to generate code for SimpleSymbolicIdEntity: Class extends 'WorkspaceEntityWithSymbolicId' but doesn't override 'WorkspaceEntityWithSymbolicId.getSymbolicId' property")
  }

  fun testInheritanceEntityAndSource() {
    doTestAndCheckErrorMessage("com.intellij.workspaceModel.test.api.IllegalEntity extends WorkspaceEntity and EntitySource at the same time, which is prohibited.")
  }

  fun testInheritanceMultiple() {
    doTestAndCheckErrorMessage("com.intellij.workspaceModel.test.api.MultipleInheritanceEntity extends multiple @Abstract entities, which is prohibited: AbstractEntity3, AnotherAbstractEntity.")
  }

  fun testInheritanceNonAbstract() {
    doTestAndCheckErrorMessage("Failed to generate code for IllegalEntity: Class 'LegalEntity' cannot be extended")
  }

  private fun doTestAndCheckErrorMessage(expectedMessage: String) {
    val exception = Assertions.assertThrows(IllegalStateException::class.java) {
      doTest()
    }
    val actualMessage = exception.message!!
    assertEquals(expectedMessage, actualMessage)
  }

  private fun doTest(processAbstractTypes: Boolean = false, explicitApiEnabled: Boolean = false, isTestModule: Boolean = false) {
    generateAndCompare(
      dirWithExpectedApiFiles = getExpectedDir(),
      dirWithExpectedImplFiles = getExpectedDir().resolve("gen"),
      processAbstractTypes = processAbstractTypes,
      explicitApiEnabled = explicitApiEnabled,
      isTestModule = isTestModule
    )
  }

  private fun getExpectedDir(): Path {
    return testDataDirectory.toPath().resolve("after")
  }
}
