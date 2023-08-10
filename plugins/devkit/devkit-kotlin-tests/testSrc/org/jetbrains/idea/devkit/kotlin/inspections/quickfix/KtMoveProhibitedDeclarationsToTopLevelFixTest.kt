// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KtCompanionObjectInExtensionInspectionTestBase

@TestDataPath("/inspections/moveProhibitedDeclarationsToTopLevelFix")
class KtMoveProhibitedDeclarationsToTopLevelFixTest : KtCompanionObjectInExtensionInspectionTestBase() {

  private val fixName = DevKitKotlinBundle.message("inspections.move.prohibited.declarations.to.top.level.fix.text")

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/moveProhibitedDeclarationsToTopLevelFix"

  fun testMoveProhibitedDeclarations() {
    doTestFixWithReferences(fixName)
  }

  fun testMoveJvmStaticDeclarations() {
    doTestFixWithReferences(fixName, refFileExtension = "java")
  }

  fun testMoveConflicts() {
    val expectedConflicts = listOf(
      "Following declarations would clash: to move function &#39;fun foo()&#39; and destination function &#39;fun foo()&#39; declared in scope ",
    )
    doTestFixWithConflicts(fixName, expectedConflicts)
  }

}
