// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KtCompanionObjectInExtensionInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/moveProhibitedDeclarationsToTopLevelFix")
abstract class KtMoveProhibitedDeclarationsToTopLevelFixTest : KtCompanionObjectInExtensionInspectionTestBase() {

  protected val fixName = "Move prohibited declarations to top level"

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/moveProhibitedDeclarationsToTopLevelFix"

  fun testMoveProhibitedDeclarations() {
    doTestFixWithReferences(fixName)
  }

  fun testMoveJvmStaticDeclarations() {
    doTestFixWithReferences(fixName, refFileExtension = "java")
  }
}
