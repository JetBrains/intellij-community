// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.ktCompanionObjectInExtensionFixture
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/createObjectAndMoveProhibitedDeclarationsFix")
abstract class KtCreateObjectAndMoveProhibitedDeclarationsFixTest(projectFixture: TestFixture<LightJavaProject>) {
  protected val companionObject by ktCompanionObjectInExtensionFixture(projectFixture,
    DevkitKtTestsUtil.TESTDATA_PATH + "inspections/createObjectAndMoveProhibitedDeclarationsFix")

  private val fixName = "Create an object and move the prohibited declarations"

  @Test
  fun testMoveProhibitedDeclarations() {
    companionObject.doTestFixWithReferences(fixName)
  }

  @Test
  fun testMoveConflicts() {
    val expectedConflicts = listOf(
      "Property logger uses property toInitLogger which will be inaccessible after move",
      "Variable a uses property g which will be inaccessible after move",
      "Function foobar() uses function quux() which will be inaccessible after move",
    )
    companionObject.doTestFixWithConflicts(fixName, expectedConflicts)
  }

}
