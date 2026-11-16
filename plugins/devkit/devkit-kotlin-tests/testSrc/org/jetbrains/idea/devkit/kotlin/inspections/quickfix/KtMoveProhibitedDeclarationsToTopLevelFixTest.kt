// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.ktCompanionObjectInExtensionFixture
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/moveProhibitedDeclarationsToTopLevelFix")
abstract class KtMoveProhibitedDeclarationsToTopLevelFixTest(projectFixture: TestFixture<LightJavaProject>) {
  protected val companionObject by ktCompanionObjectInExtensionFixture(projectFixture,
    DevkitKtTestsUtil.TESTDATA_PATH + "inspections/moveProhibitedDeclarationsToTopLevelFix")

  protected val fixName = "Move prohibited declarations to top level"

  @Test
  fun testMoveProhibitedDeclarations() {
    companionObject.doTestFixWithReferences(fixName)
  }

  @Test
  fun testMoveJvmStaticDeclarations() {
    companionObject.doTestFixWithReferences(fixName, refFileExtension = "java")
  }
}
